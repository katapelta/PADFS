package padfsThreads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingWorker;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;

import jobManagement.consensus.Accept;
import jobManagement.consensus.JobConsMsg;
import jobManagement.consensus.Prepare;
import jobManagement.consensus.Propose;
import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.manageOp.ExitOperation;
import jobManagement.jobOperation.manageOp.JobInternalOp;
import restInterface.RestInterface;
import restInterface.consensus.ProposalNumber;
import restInterface.consensus.RestAcceptResponse;
import restInterface.consensus.RestProposeResponse;
import restInterface.consensus.RestReply;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Variables;
import system.consensus.ConsensusServerGroup;
import system.consensus.ConsensusVariables;
import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managementOp.SynchGlobal;
import system.managementOp.SynchGroup;

public class Consensus extends Thread {

	private Proposer propThread;
	private Acceptor acceptThread;

	private Long currentProposerConsGroupId;
	private JobOperation currentProposerPendingOp;

	private boolean receivedExitOperation = false;

	public Consensus(
			PriorityBlockingQueue<JobOperation> inConsOp, 
			BlockingQueue<JobConsMsg<?>> 		inConsMsg,
			BlockingQueue<JobOperation> outConsOp) {
		BlockingQueue<Notification> notificationQueue = new LinkedBlockingQueue<Notification>();
		propThread 				   = new Proposer(inConsOp, outConsOp, notificationQueue);
		acceptThread 			   = new Acceptor(inConsMsg, outConsOp, notificationQueue);
		currentProposerConsGroupId = null;

	}

	public void run() {
		propThread.setName("PROPOSER");
		propThread.start();
		
		acceptThread.setName("ACCEPTOR");
		acceptThread.start();

	}


	private boolean majorityReached(ConsensusServerGroup serverList, int number) {
		return number > (serverList.size() / 2);
	}

	private class Proposer extends Thread {		
		private PriorityBlockingQueue<JobOperation> inConsOp;
		private BlockingQueue<JobOperation> outConsOp;
		private BlockingQueue<Notification> notificationQueue;
		private JobOperation currentOp;
		private ConsensusServerGroup serverList;
		private ConsensusVariables consensusVar;
		private long consensusGroupId;

		private Vector<RestReply> positiveReplies = new Vector<>(SystemEnvironment.Constants.replicaNumber);
		private Vector<RestReply> negativeReplies = new Vector<>(SystemEnvironment.Constants.replicaNumber);

		private boolean finished;
		private boolean majorityReplyReached;
		private int positiveReply;
		private int negativeReply;
		private ArrayList<ListenableFuture<ResponseEntity<RestReply>>> replies = new ArrayList<>();
		private Notification notification;
		private ProposalNumber currentProposalNumber;
		private String currentClientOpId;

		private int sendPrepareMsgStatus = 0;
		

		public Proposer(PriorityBlockingQueue<JobOperation> inConsOp, BlockingQueue<JobOperation> outConsOp,
				BlockingQueue<Notification> notificationQueue) {
			this.notificationQueue = notificationQueue;
			this.inConsOp = inConsOp;
			this.outConsOp = outConsOp;
			this.finished = false;
			this.consensusGroupId = -1;
		}

		public void run() {
			while (!receivedExitOperation) {
				try{
					PadFsLogger.log(LogLevel.DEBUG, "(PROPOSER) WAITING For Operation");
					currentOp = inConsOp.take();
					
					if(JobInternalOp.class.isAssignableFrom(currentOp.getClass())){
						JobInternalOp op = (JobInternalOp) currentOp;
						/* no need to run consensus for internalOp */
						manageInternalOp(op);
						continue;
					}
					
					
				} catch (InterruptedException e) {
					//PadFsLogger.log(LogLevel.FATAL, "(PROPOSER) STOP BECAUSE OF INTERRUPT EXCEPTION: "+e.getMessage());
					continue;
				}
				PadFsLogger.log(LogLevel.DEBUG,	"(PROPOSER) START problema del consenso: <"+ currentOp.getIdOp() + ">");

				/* retrieve some environment variables */
				serverList = currentOp.consensusGroupServerList();
				if(serverList == null){
					PadFsLogger.log(LogLevel.ERROR, "failed retrieving consensusGroupServerList");
					//waiting next message 
					continue;
				}
				consensusGroupId = serverList.getConsensusGroupId();
				PadFsLogger.log(LogLevel.TRACE, "consensusGroupId = "+consensusGroupId);
				consensusVar = Variables.consensusVariableManager.getConsVariables(consensusGroupId);
				if(consensusVar!= null)
					PadFsLogger.log(LogLevel.TRACE, "consensusVar.HPN = "+consensusVar.getHpn()	);
				else
					PadFsLogger.log(LogLevel.DEBUG, "consensusVaris null");
				
					
					
				currentClientOpId = currentOp.getIdOp();

				/* enable Acceptor notification from the correct consensusGroup */
				if (!setActualProposerConsensusGroup(consensusGroupId,currentOp)) {
					PadFsLogger.log(LogLevel.ERROR, "consensusGroupId cannot be set");
					currentOp.replyError(Rest.errors.internalError);
					finished = true;
				} else {
					finished = false;
				}

				int trialSendPrepare = 0;
				boolean failed = true;
				while(trialSendPrepare < Variables.getRetryNumber() && !finished && !receivedExitOperation ){
					
					trialSendPrepare ++;
					resetProposerVariables();
					PadFsLogger.log(LogLevel.DEBUG,	"serverList ("+trialSendPrepare+" trial sendPrepareMessage) size:"+serverList.size() );
	
					sendPrepareMsg(currentOp,	consensusVar, serverList);
					
					
	
	/**
	 * +----------------------------------------------------------------------------------+--------------------------------------------------+------------------+   
	 * |              CASO                                                                |       STATO                                      |    AZIONE        |
	 * +----------------------------------------------------------------------------------+--------------------------------------------------+------------------+   
	 * | 1) NOTIFICATION_PROPNUM < CURRENT_PROPNUM                                        | PROPOSTA NATA PRIMA DELLA MIA                    | ASPETTARE        |
	 * | 2) NOTIFICATION_PROPNUM = CURRENT_PROPNUM && NOTIFICATION_IDOP  = CURRENT_IDOP   | MIA PROPOSTA                                     | HO FINITO        |
	 * | 3) NOTIFICATION_PROPNUM = CURRENT_PROPNUM && NOTIFICATION_IDOP != CURRENT_IDOP   | IO HO VOTATO QUALCUN'ALTRO                       | DEVO RIPROVARE   |
	 * | 4) NOTIFICATION_PROPNUM > CURRENT_PROPNUM && NOTIFICATION_IDOP  = CURRENT_IDOP   | QUALCUN'ALTRO HA VOTATO PER ME                   | HO FINITO        |
	 * | 5) NOTIFICATION_PROPNUM > CURRENT_PROPNUM && NOTIFICATION_IDOP != CURRENT_IDOP   | PROPOSTA PIU' RECENTE DELLA MIA E' STATA VOTATA  | DEVO RIPROVARE   |
	 * +----------------------------------------------------------------------------------+--------------------------------------------------+------------------+   
	 */
					//receivedExitOperation a true  quando è stato chiesto di terminare il thread
					//finished  a true  quando la mia proposta è stata accettata
					//failed    a true  quando la mia proposta è stata scartata
					failed = false;
					while (!finished && !failed ) {  
						/* && !receivedExitOperation 
						 * in questo punto ignoriamo la richiesta di chiusura del thread Consenso per poter rispondere
						 * correttamente al client riguardo alla sua operazione
						 */
						try{
							PadFsLogger.log(LogLevel.DEBUG, "(PROPOSER) wait for notification");
							
							String nProp = "null";
							String cProp = "null";
							String nIdOp = "null";
							String cIdOp = "null";
							
							if(notification!=null){
								nProp = (notification.getProposalNumber()!=null)?String.valueOf(notification.getProposalNumber()):"null";
								nIdOp = (notification.getidOp()!=null)?String.valueOf(notification.getidOp()):"null";
							}	
							cProp = (currentProposalNumber!=null)?String.valueOf(currentProposalNumber):"null";
							cIdOp = (currentClientOpId!=null)?String.valueOf(currentClientOpId):"null";
							
							PadFsLogger.log(LogLevel.DEBUG, "\n\t NOTIFICATION_PROPNUM: "+nProp
									+"\n\t CURRENT_PROPNUM: "+cProp
									+"\n\t NOTIFICATION_IDOP: "+nIdOp
									+"\n\t CURRENT_IDOP: "+cIdOp);
									
							
							notification = notificationQueue.poll(Constants.waitProposerTimeout, TimeUnit.SECONDS); //.take();
							if(notification==null){
								PadFsLogger.log(LogLevel.DEBUG, ">>> (PROPOSER) NOTIFICATION TIMEOUT");
								failed=true;
								break;
							}
							if(notification.isRejected()){
								PadFsLogger.log(LogLevel.DEBUG, "the proposer has received negative Reply. retry consensus Prepare");
								try{
									Thread.sleep(Constants.waitBeforeRetryConsensus);
								}catch (InterruptedException e) { ; }
								failed=true;
								break;
							}
						} catch (InterruptedException e) {
							//PadFsLogger.log(LogLevel.FATAL, "(PROPOSER) STOP BECAUSE OF INTERRUPT EXCEPTION: "+e.getMessage());
							//terminate = true;
							continue;						
						}
	
						if(currentProposalNumber.greaterThan(notification.getProposalNumber())){
							//  case 1: ( propNum < mioPropNum )    nothing to do
							PadFsLogger.log(LogLevel.DEBUG, "(PROPOSER)  receive a case 1 notification");
							
						}else if((	currentProposalNumber.equals(notification.getProposalNumber())  &&  
									currentClientOpId.equals(notification.getidOp())) 
								||
								(	notification.getProposalNumber().greaterThan(currentProposalNumber) &&
									currentClientOpId.equals(notification.getidOp())))
						{
							//  case 2,4:  ho finito
							finished = true;
	
							PadFsLogger.log(LogLevel.DEBUG,"(PROPOSER)  Consensus completed: <"+ currentOp.getIdOp() + ">");
							/*
							 * unregister from notifications and empty the
							 * notification queue
							 */
							deleteActualProposerConsensusGroup();
							while (!notificationQueue.isEmpty()) {
								try{
									notificationQueue.take();
								} catch (InterruptedException e) {
									//PadFsLogger.log(LogLevel.FATAL, "(PROPOSER) STOP BECAUSE OF INTERRUPT EXCEPTION: "+e.getMessage());
									//terminate = true;
									continue;						
								}
							}						
							
						}else if((	currentProposalNumber.equals(notification.getProposalNumber())  &&  
									currentClientOpId.equals(notification.getidOp()))
								||
								(	notification.getProposalNumber().greaterThan(currentProposalNumber) &&
									!currentClientOpId.equals(notification.getidOp())))
						{
							PadFsLogger.log(LogLevel.DEBUG, "(PROPOSER) receive a case 3,5 notification");
							
							// case 3,5: retry  
							
							/*
							 * la nostra proposta è stata scartata.
							 * Alla prossima iterazione (del while esterno) devo ripetere la proposta 
							 *  se trial è minore del numero massimo di tentativi
							 */
							failed = true;  
							
							//resetProposerVariables();
							//sendPrepareMsg(currentOp,	consensusVar, serverList);
							
							
						}
					}
					
					
				
				}	
				
				if(failed){
					// La proposta è stata scartata ripetutamente per il numero massimo di tentativi disponibili.
					PadFsLogger.log(LogLevel.DEBUG, "Consensus not reached");
					currentOp.replyError(Rest.errors.consensusNotReached);
				}
				
			}
			PadFsLogger.log(LogLevel.INFO, "Consensus.Proposer shutdown");
		}

		private void manageInternalOp(JobInternalOp currentOp) {
			if(currentOp.getClass().equals(ExitOperation.class)) {
				receivedExitOperation = true;
			}
			
			/* forward the operation out of the consensus */
			outConsOp.add(currentOp);
		}

		private synchronized void resetProposerVariables() {
			// ignore old async reply
			sendPrepareMsgStatus++;
			PadFsLogger.log(LogLevel.DEBUG, "---resetProposerVariables");

			majorityReplyReached = false;
			positiveReply = 0;
			negativeReply = 0;
		}

		private void sendPrepareMsg(JobOperation currentOperation,	ConsensusVariables consVar, ConsensusServerGroup serverList) {
			// send prepare message to selected servers
			String URL_PREPARE = null;
			long idConsRun;

			Iterator<Server> iter = null;
			Server server = null;

			AsyncRestTemplate restTemplate = null;

			ListenableFuture<ResponseEntity<RestReply>> reply = null;

			/* make prepare message */
			idConsRun = consVar.getIdConsRun();
			currentProposalNumber = new ProposalNumber(consVar.getHpn());
			
		
			/* send prepare to serverList */
			PadFsLogger.log(LogLevel.DEBUG,	"START sending prepare messages");
			iter = serverList.iterator();
			while (iter.hasNext()) {

				final Integer currentStatus = sendPrepareMsgStatus;

				server = iter.next();

				PadFsLogger.log(LogLevel.DEBUG,	"send prepare message to "+server.getIp()+":"+server.getPort());
				
				restTemplate = SystemEnvironment.generateAsyncRestTemplate();
				if (server.getIp() != null && server.getPort() != null) {
					
					URL_PREPARE = RestInterface.Prepare.generateUrl(server.getIp(),server.getPort(),
																	currentOperation, 
																	currentProposalNumber, 
																	idConsRun);
					
					
					PadFsLogger.log(LogLevel.TRACE,	"URL_PREPARE: " + URL_PREPARE);
					try{
						reply = restTemplate.exchange(URL_PREPARE, HttpMethod.GET,null, RestReply.class);
					
						replies.add(reply);


						PadFsLogger.log(LogLevel.DEBUG,	"SEND PREPARE TO: "+server.getIp() + ":" + server.getPort());

						reply.addCallback(new ListenableFutureCallback<ResponseEntity<RestReply>>() {
							
							
							@Override
							public void onSuccess(ResponseEntity<RestReply> res) {
								try{
									manageReply(consVar, serverList, res.getBody(),	currentStatus);
									PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK - RestReply messagge SUCCESS" );
								} catch (Exception e) {
									PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestReply onSuccess FAILURE: "	+ e.getMessage());				
									e.printStackTrace();
								}								
							}
	
							@Override
							public void onFailure(Throwable e) {
								try{
									manageReply(consVar, serverList, null, currentStatus);
								} catch (Exception e2) {
									e2.printStackTrace();
									PadFsLogger.log(LogLevel.ERROR,	"CALLBACK - onFailure manageReply FAILURE: "	+ e2.getMessage());									
								}

								PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestReply messagge FAILURE");
								PadFsLogger.log(LogLevel.TRACE,	"CALLBACK - RestReply messagge FAILURE: "+e.getMessage());

																
							}
						});
						
					}
					catch(Exception e){
						PadFsLogger.log(LogLevel.TRACE,	e.getMessage());
						PadFsLogger.log(LogLevel.WARNING,	"impossible to communicate with "+server.getIp()+":"+server.getPort());
						return;
					}
					
				} else {
					PadFsLogger.log(LogLevel.ERROR,	"ip:port malformed: "	+ server.getIp() + ":" + server.getPort());
				}
			}
			PadFsLogger.log(LogLevel.DEBUG,	"END sending prepare messages");
			return;
		}

		/**
		 * conto risposte positive e negative. controllo che ci sia una
		 * maggioranza se c'e' una maggioranza positiva, spedisce propose
		 * altrimenti notifica al thread proposer
		 */
		private synchronized void manageReply(ConsensusVariables consVar,ConsensusServerGroup serverList, RestReply reply, Integer currentStatus) {

			if (currentStatus != sendPrepareMsgStatus) {
				return;
			}

			ProposalNumber proposalNumber = null;
			String logPropNum = "null";
			if(reply != null ){
				proposalNumber = reply.getProposalNumber();
				if(proposalNumber != null)
					logPropNum = proposalNumber.toJSON();
			}
			
//			int idConsensusRun = reply.getIdConsensusRun();
			PadFsLogger.log(LogLevel.DEBUG,"majorityReplyReached = "+majorityReplyReached);
			
			if (majorityReplyReached == false) {
				if (reply != null	&& reply.getAck().equals(Constants.Rest.status.ack)) {
					positiveReplies.add(reply);
					positiveReply++;
					PadFsLogger.log(LogLevel.DEBUG,	"proposal ack proNum:"+ logPropNum);
					PadFsLogger.log(LogLevel.TRACE,	"REPLY ACK");
				} else {
					if(reply != null){
						negativeReplies.add(reply);
						PadFsLogger.log(LogLevel.TRACE,	"REPLY NACK");
						/* if I miss some group operations, execute a groupSynch */
						if(reply != null && reply.getIdConsensusRun() > consVar.getIdConsRun() && consVar.getConsensusGroupId() != Constants.globalConsensusGroupId){
	
							PadFsLogger.log(LogLevel.DEBUG,	"delayedGroupSynch AFTER a REPLY NACK");
							SynchGroup.delayedGroupSynch(consVar.getConsensusGroupId());
						}
					}
					negativeReply++;
			
					PadFsLogger.log(LogLevel.DEBUG,	"proposal nack propNum:"+ logPropNum);
				}

				PadFsLogger.log(LogLevel.DEBUG,	"(idOp: "+currentOp.getIdOp()+") -> serverListSize: "+serverList.size()+"  - positiveReply:"+positiveReply+" -  negativeReply:"+ negativeReply);
				
				if (majorityReached(serverList, positiveReply)) {
					// if Reply(Va, Na) from Majority
					//    V' = Va with greatest Na
					//    if V' = 0 then
					//    V' = V
					JobOperation v = getHighestNa(positiveReplies);
					if (v == null) {
						v = currentOp;
					}

					PadFsLogger.log(LogLevel.DEBUG,"proposal ready propNum:"+ logPropNum);
					PadFsLogger.log(LogLevel.DEBUG,"sending propose..");
					try{
						PadFsLogger.log(LogLevel.DEBUG,"isSetcurrentProposalNumber = "+ (currentProposalNumber!=null));
						PadFsLogger.log(LogLevel.DEBUG,"reply"+reply.toJSON());
						
						sendProposeMsg(v, v.consensusGroupServerList(), currentProposalNumber, consVar);
					}
					catch(Exception e){
						PadFsLogger.log(LogLevel.ERROR, "can't send propose messages due to "+e.getMessage());
						
					}
					majorityReplyReached = true;
					PadFsLogger.log(LogLevel.DEBUG,"(after sendingPropose) majorityReplyReached = "+majorityReplyReached);
					
				} else if (majorityReached(serverList, negativeReply)) {
					PadFsLogger.log(LogLevel.DEBUG,"proposal refused propNum:"	+ logPropNum);
					majorityReplyReached = true;

					PadFsLogger.log(LogLevel.DEBUG,"(after sendingPropose (negativeCase)) majorityReplyReached = "+majorityReplyReached);
					
					/* set the highest HPN seen in responses */
					ProposalNumber hpnReplies = getHighestHpn(negativeReplies);
					if(hpnReplies != null){
						consVar.setHPN( hpnReplies );
					}
					Notification notify = new Notification(null, null,true);
					try{
						/* notify negativeMajority so that proposer can retry */
						notificationQueue.put(notify);
					}
					catch(InterruptedException e){ ; }
					
				}
			}
		}

		private ProposalNumber getHighestHpn(Vector<RestReply> negativeReplies) {
				ProposalNumber higher = null;
				
				for(RestReply reply : negativeReplies){
					if(reply != null){
						if(reply.getHighestProposalNumber() != null){
							if(higher == null){
								higher = reply.getHighestProposalNumber();
							}
							else{
								if(!higher.greaterThan(reply.getHighestProposalNumber())){
									higher = reply.getHighestProposalNumber();
								}
							}
						}
					}
				}
				
			
				return higher;
			
		}

		private void sendProposeMsg(JobOperation v,	ConsensusServerGroup consensusGroupServerList, ProposalNumber currentProposalNumber, ConsensusVariables consVar) {
			// send propose message to selected servers
			String proposeRequest = null;
			long idConsRun;

			Iterator<Server> iter = null;
			Server server = null;

			AsyncRestTemplate restTemplate = null;

			/* make propose message */
			idConsRun = consVar.getIdConsRun();
			
			PadFsLogger.log(LogLevel.DEBUG,"currentProposalNumber = "+ currentProposalNumber);
			
					

			/* send propose to serverList */
			iter = serverList.iterator();
			while (iter.hasNext()) {
				server = iter.next();

				restTemplate = SystemEnvironment.generateAsyncRestTemplate();
				if (server.getIp() != null && server.getPort() != null) {
					
					proposeRequest = RestInterface.Propose.generateUrl(server.getIp(),server.getPort(), v, currentProposalNumber, idConsRun);				
					sendExecutePropose(server, restTemplate, proposeRequest, Variables.getRetryNumber());
				
				} else {
					PadFsLogger.log(LogLevel.ERROR,	"ip:port malformed: "	+ server.getIp() + ":" + server.getPort());
				}
			}
		}
		
		private void sendExecutePropose(Server server, AsyncRestTemplate restTemplate, String proposeRequest, int retryNumber){
			if(retryNumber<=0)
				return ;
			
			String URL_PROPOSE = null;
			ListenableFuture<ResponseEntity<RestProposeResponse>> reply = null;

			URL_PROPOSE = proposeRequest;

			try{
				reply = restTemplate.exchange(URL_PROPOSE, HttpMethod.GET,null, RestProposeResponse.class);
			
				PadFsLogger.log(LogLevel.DEBUG,	"SEND PROPOSE TO: "+server.getIp()+":"+server.getPort());
				PadFsLogger.log(LogLevel.TRACE,	"URL_PROPOSE: " + URL_PROPOSE);	
	
				reply.addCallback(new ListenableFutureCallback<ResponseEntity<RestProposeResponse>>() {
					@Override
					public void onSuccess(ResponseEntity<RestProposeResponse> res) {
						if(res != null && res.getBody() != null){
							if(res.getBody().getAck() == Rest.status.ack){
								PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK - RestProposeResponse messagge: OK - ID CONS RUN: "+res.getBody().getIdConsRun());
							}else{
								PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK - RestProposeResponse messagge: Not Ack : "+res.getBody().getAck().toString()+" - "+res.getBody().getIdConsRun() );
								//maybe I'm out of synch
							}
						}
						else{
							PadFsLogger.log(LogLevel.WARNING, "failed communicate");
						}
					}
	
					@Override
					public void onFailure(Throwable e) {
						PadFsLogger.log(LogLevel.WARNING,	"CALLBACK - RestProposeResponse messagge: "	+ e.getMessage());
	
						try {
							Thread.sleep(Variables.getWaitMillisecondsBeforeRetry());
						} catch (InterruptedException e1) {
							PadFsLogger.log(LogLevel.ERROR,	"CALLBACK - WAIT THREAD ERROR: "	+ e1.getMessage());
						}
						sendExecutePropose(server, restTemplate, proposeRequest, (retryNumber-1));
					}
				});
				
			}
			catch(Exception e){
				PadFsLogger.log(LogLevel.DEBUG,	"impossible to communicate with "+server.getIp()+":"+server.getPort());
				return;
			}
		}

		private JobOperation getHighestNa(Vector<RestReply> pr) {
			RestReply h = null;
			JobOperation ret = null;
			for (int i = 0; i < pr.size(); i++) {
				if (h == null) {
					h = pr.get(i);
				} else {
					if (pr.get(i).getProposalNumber().greaterThan(h.getProposalNumber())) {
						h = pr.get(i);
					}
				}
			}
			if (h != null)
				ret = h.objectOperation();
			return ret;
		}
	}

	private class Acceptor extends Thread {
		private BlockingQueue<JobConsMsg<?>> inConsMsg;
		private BlockingQueue<JobOperation> outConsOp;
		private BlockingQueue<Notification> notificationQueue;

		private HashMap<String,Integer> countAccept = new HashMap<>();
		private HashMap<String,JobOperation> opAccept = new HashMap<>();

		JobConsMsg<?> currentConsMsg = null;

		public Acceptor(BlockingQueue<JobConsMsg<?>> inConsMsg,BlockingQueue<JobOperation> outConsOp,BlockingQueue<Notification> notificationQueue) {
			this.inConsMsg = inConsMsg;
			this.outConsOp = outConsOp;
			this.notificationQueue = notificationQueue;
		}

		public void run() {
			ConsensusVariables consVar = null;
			ConsensusServerGroup serverList = null;

			while (receivedExitOperation == false) {
				try {
					currentConsMsg = inConsMsg.take();
					if(!currentConsMsg.checkPassword()){
						PadFsLogger.log(LogLevel.ERROR, "WRONG PASSWORD in consensus message. ignore it.");
						continue;
					}
				} catch (InterruptedException e) {
					//PadFsLogger.log(LogLevel.FATAL, "ACCEPTOR STOP BECAUSE OF INTERRUPT EXCEPTION: "+e.getMessage());	
					continue;
				}
				
				
				serverList = currentConsMsg.consensusGroupServerList();
				if(serverList == null){
					PadFsLogger.log(LogLevel.WARNING, "cannot retrieve consensusGroupId from currentConsMsg" );
					continue;
				}
				
				long consGroupId = serverList.getConsensusGroupId();
				consVar = Variables.consensusVariableManager.getConsVariables(consGroupId);
				PadFsLogger.log(LogLevel.DEBUG, "Gestione messaggio consenso: "	+ "<" + currentConsMsg.getOp().getIdOp() + ">");

				if (currentConsMsg.getIdConsRun() > consVar.getIdConsRun()) {
					/**
					 * Synch required. 
					 * check if SynchGlobal or SynchGroup
					 */
					
					PadFsLogger.log(LogLevel.DEBUG, "msg.idConsRun: ("+ currentConsMsg.getIdConsRun()+ ") > DB.idConsRun (" + consVar.getIdConsRun()	+ ") => synchronization needed. Acceptor received a message with an higher consRunId");

					if(consGroupId == Constants.globalConsensusGroupId){
						//SynchGlobal. try to synch with all the servers in the net

						PadFsLogger.log(LogLevel.TRACE,"scheduling a globalSynch");
						SynchGlobal.delayedGlobalSynch();
							/*
							 * also if the synchronization is not completed correctly
							 * do not stop thread consensus. At the next message it will retry with the synch.
							 * Now we simply ignore the message as if this server is unreachable
							 */
						
					}
					else{
						PadFsLogger.log(LogLevel.TRACE,"scheduling a groupSynch for group "+consGroupId);
						SynchGroup.delayedGroupSynch(consGroupId);
							/*
							 * also if the synchronization is not completed correctly
							 * do not stop thread consensus. At the next message it will retry with the synch.
							 * Now we simply ignore the message as if this server is unreachable
							 */
						
						
					}
					
					
				} else if (currentConsMsg.getIdConsRun() < consVar.getIdConsRun()) {
					// if the sender of this message is out of sync
					// 		ignore the message
					// 		send notification (nack,currentIdConsRun) to the sender of this message

					PadFsLogger.log(LogLevel.TRACE, "SENDING NACK");
					boolean answerResult = currentConsMsg.answer(Rest.status.nack,consVar.getIdConsRun());
					if(!answerResult){
						PadFsLogger.log(LogLevel.DEBUG, "Failed to provide an answer of an IGNORED message");
					}
					PadFsLogger.log(LogLevel.DEBUG, "msg.idConsRun: ("+ currentConsMsg.getIdConsRun() + ") < DB.idConsRun (" + consVar.getIdConsRun() + ") => ignore message "+currentConsMsg.getClass().getName());

				} else {
					manageConsMsg(currentConsMsg, consVar, outConsOp);
					
				}
			}
			
			PadFsLogger.log(LogLevel.INFO, "Consensus.Acceptor shutdown");
		}

		private synchronized void manageConsMsg(JobConsMsg<?> currentConsMsg,ConsensusVariables consVar, BlockingQueue<JobOperation> outConsOp) {
			// prepare, propose, decided

			switch (currentConsMsg.getClass().getName().toString()) {
			case "jobManagement.consensus.Prepare": 													// 3) HandlePrepare(v,n)

				PadFsLogger.log(LogLevel.DEBUG, "(PREPARE) Msg arrived");
				Prepare msgPrepare = (Prepare) currentConsMsg;

				
				if (msgPrepare.getProposalNumber().greaterThan(consVar.getHpn())) { 						 // if( N > Np ) then
					consVar.setHPN(msgPrepare.getProposalNumber());                  						 //	 Np = N
					if(!msgPrepare.answer(Constants.Rest.status.ack,consVar.getHapv(), consVar.getHapn())){  //  Reply (Va, Na)
						PadFsLogger.log(LogLevel.DEBUG, "answer failed (prepare N>Np)");
					}
				} else {
					if(!msgPrepare.answer(Constants.Rest.status.nack,consVar.getHapv(), consVar.getHapn(), consVar.getHpn())){  
						PadFsLogger.log(LogLevel.DEBUG, "answer failed (prepare)");
					}
				}
				break;

			case "jobManagement.consensus.Propose": // 6) HandlePropose(V', N)

				PadFsLogger.log(LogLevel.DEBUG, "(PROPOSE) Msg arrived");
				Propose msgPropose = (Propose) currentConsMsg;
				

				if (msgPropose.getProposalNumber().greaterEqualThan(consVar.getHpn())) { 	// if(N >= Np)then
					consVar.setHPN(msgPropose.getProposalNumber());							// 		Np = N
					consVar.setHapn(msgPropose.getProposalNumber()); 						// 		(Va,Na) = (V',N)
					consVar.setHapv(msgPropose.getOp());

					if(!msgPropose.answer(Constants.Rest.status.ack)){
						PadFsLogger.log(LogLevel.DEBUG, "answer ack failed (propose N>=Np)");
					}
					
					long actualIdConsRun = consVar.getIdConsRun();
					sendBroadcastAccept(consVar,actualIdConsRun,currentConsMsg.consensusGroupServerList()); // 		ACCEPT(V',N)

				} else {
					if(!msgPropose.answer(Constants.Rest.status.nack)){
						PadFsLogger.log(LogLevel.DEBUG, "answer ack failed (propose N<Np)");
					}
				}

				break;

			case "jobManagement.consensus.Accept":
				
				PadFsLogger.log(LogLevel.DEBUG, "(ACCEPT) - Msg arrived");
				Accept msgAccept = (Accept) currentConsMsg;
				
				JobOperation op = msgAccept.getOp();
				String idOp = op.getIdOp();
				int count = 1;
				
				/* store the accept messages */
				if(opAccept.containsKey(idOp)){
					count = countAccept.get(idOp) + 1;
				}
				else{
					opAccept.put(idOp,msgAccept.getOp());
				}
				
				if(!msgAccept.answer(Rest.status.ack)){
					PadFsLogger.log(LogLevel.DEBUG, "answer ack failed (accept)");
				}
				
				countAccept.put(idOp,count);
				
				/* check if a majority is reached  */
				ConsensusServerGroup consGroup = op.consensusGroupServerList();
				Long consGroupId = consGroup.getConsensusGroupId();
			    if(majorityReached(consGroup, count)){
			    	
			    	{
			    		// if the operation is proposed by this server, retrieve the operation by the proposer (it contains the DeferredResult<> )
			    		JobOperation proposerOp = getCurrentProposerPendingOp();
			    		if(proposerOp != null)
			    			if(proposerOp.getIdOp().equals(op.getIdOp())){ // be sure that they are the same operation
			    				op = proposerOp;
			    				deleteActualProposerPendingOp();
			    			}
			    	}
			    	
			    	/* insert in outConsOp the operation */
			    	op.setIdConsRun( Variables.consensusVariableManager.getConsVariables(consGroupId).getIdConsRun() );
			    	if(!outConsOp.add(op)){
			    		PadFsLogger.log(LogLevel.FATAL, "Insertion in outConsOp FAILED");
			    		//terminate the execution of the server

			    	}else{
			    		/* incr idConsensusRun */
			    		Variables.consensusVariableManager.getConsVariables(consGroupId).nextConsensus();
			    		
			    		long idConsRun = Variables.consensusVariableManager.getConsVariables(consGroupId).getIdConsRun();
			    		
			    		PadFsLogger.log(LogLevel.DEBUG, "(ACCEPT) - Insertion in outConsOp. idConsensus:"+consGroupId+" idConsRun:"+idConsRun);
			    	}
			    	
			    	/* notify to the Proposer, if it is registered in this group */

		    		PadFsLogger.log(LogLevel.DEBUG, "getCurrentProposerConsGroupId(): "+getCurrentProposerConsGroupId() + ",  op.consensusGroupServerList().getConsensusGroupId(): "+consGroupId);
			    	if(getCurrentProposerConsGroupId() == consGroupId){
				    	Notification notify = new Notification(msgAccept.getProposalNumber(),op.getIdOp(),false);
				    	notificationQueue.add(notify);
			    	}			    	    	
			    }
			    PadFsLogger.log(LogLevel.DEBUG, "END MANAGE ACCEPT"); 
				break;
			}
		}

		private void sendBroadcastAccept(ConsensusVariables consVar,long acceptingIdConsRun, ConsensusServerGroup consensusGroupServerList) {
			Server server = null;
			Iterator<Server> servers = consensusGroupServerList.iterator();
			while (servers.hasNext()) {
				server = servers.next();


				String acceptRequest;
				acceptRequest = RestInterface.Accept.generateUrl(server.getIp(),server.getPort(), consVar.getHapv(), consVar.getHapn(), consVar.getIdConsRun());
				
				sendAccept(server, acceptRequest, Variables.getRetryNumber(), acceptingIdConsRun, consVar);
			}

		}

		private void sendAccept(Server server, String URL_ACCEPT, int retryNumber, long URLidConsRun, ConsensusVariables consVar) {
			AsyncRestTemplate restTemplate = SystemEnvironment.generateAsyncRestTemplate();

			if (retryNumber <= 0)
				return;
			
			if (server.getIp() != null && server.getPort() != null) {
				ListenableFuture<ResponseEntity<RestAcceptResponse>> response;
				
				try{
					response = restTemplate.exchange(URL_ACCEPT, HttpMethod.GET, null,	RestAcceptResponse.class);
				
				
					PadFsLogger.log(LogLevel.DEBUG, "SEND ACCEPT TO: "+server.getIp()+":"+server.getPort()+" - trialNumber: " + (1+Variables.getRetryNumber()-retryNumber));
	
					PadFsLogger.log(LogLevel.TRACE, "URL_ACCEPT: "+ URL_ACCEPT + "  trialNumber: " + (1+Variables.getRetryNumber()-retryNumber));
					ListenableFutureCallback<ResponseEntity<RestAcceptResponse>> callbacks = new ListenableFutureCallback<ResponseEntity<RestAcceptResponse>>() {
						@Override
						public void onSuccess(ResponseEntity<RestAcceptResponse> res) {
							
							/* if it is a nack message */
							if( res != null  &&  res.getBody()!=null && res.getBody().getIdConsRun() != null && res.getBody().getAck()==Rest.status.nack){
								long RESidConsRun = res.getBody().getIdConsRun();
								if( RESidConsRun <= URLidConsRun){
									// ha già fatto la complete op dell'operazione contenuta in questa accept  ( nothing to do )
									PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK Accept sent - RESPONSE idConsRun: " + RESidConsRun +"<= SEND idConsRun: " +  URLidConsRun );
									PadFsLogger.log(LogLevel.TRACE,	"CALLBACK Accept sent - RESPONSE idConsRun: " + RESidConsRun +"<= SEND idConsRun: " +  URLidConsRun + "|  AcceptResponse received: "+ URL_ACCEPT  );
	
								}
								if(RESidConsRun > consVar.getIdConsRun() ){
									PadFsLogger.log(LogLevel.DEBUG,	"CALLBACK Accept sent - RESPONSE idConsRun: " + RESidConsRun +"> CONSVAR idConsRun: " +  consVar.getIdConsRun() );
									PadFsLogger.log(LogLevel.TRACE,	"CALLBACK Accept sent - RESPONSE idConsRun: " + RESidConsRun +"> CONSVAR idConsRun: " +  consVar.getIdConsRun() + "| AcceptResponse received: "+ URL_ACCEPT  );
	
			
									//check if the groupId is not the globalOne
									if(consVar.getConsensusGroupId() != Constants.globalConsensusGroupId){
										//wait for a possible delay in the net and synch
										SynchGroup.delayedGroupSynch(consVar.getConsensusGroupId());      
										
									}
									else{
										SynchGlobal.delayedGlobalSynch();
									}
										
									
								}
							}
							
						}
	
						@Override
						public void onFailure(Throwable e) {
							PadFsLogger.log(LogLevel.WARNING,"CALLBACK - Accept failed: " + e.getMessage() + "\n\t" + e.getLocalizedMessage());
							PadFsLogger.log(LogLevel.TRACE,"CALLBACK - Accept failed: "+ URL_ACCEPT + "\n\t"	+ e.getMessage() + "\n\t" + e.getLocalizedMessage());
							
							//e.printStackTrace();
							try {
								Thread.sleep(Variables.getWaitMillisecondsBeforeRetry());
							} catch (InterruptedException e1) {;}
	
							sendAccept(server, URL_ACCEPT, retryNumber - 1, URLidConsRun, consVar);
						}
					};
					
					LongRunProcess asyncTask = new LongRunProcess(response,callbacks);
					asyncTask.execute();
					
				}
				catch(Exception e){
					PadFsLogger.log(LogLevel.WARNING, "impossible to communicate with "+server.getIp()+":"+server.getPort()+" - trialNumber: " + (1+Variables.getRetryNumber()-retryNumber));
					return;
				}
			}
			
		}

	}
	
	private class LongRunProcess extends SwingWorker<Object,Object> {
        /**
         * @throws Exception
         */
		private ListenableFuture<ResponseEntity<RestAcceptResponse>> response;
		private ListenableFutureCallback<ResponseEntity<RestAcceptResponse>> callbacks;
		
		public LongRunProcess(ListenableFuture<ResponseEntity<RestAcceptResponse>> response,
								ListenableFutureCallback<ResponseEntity<RestAcceptResponse>> callbacks ) {
			this.response = response;
			this.callbacks = callbacks;
		}
		
        protected Object doInBackground() throws Exception {
        	response.addCallback(callbacks);
            return null;
        }
    }

	
	

	/*
	 * questi metodi servono per la comunicazione di proposer e acceptor. no
	 * serve la sincronizzazione perchè entrambi i metodi vengono chiamati solo
	 * dal proposer.
	 */

	public synchronized boolean setActualProposerConsensusGroup(Long consensusId,JobOperation pendingOp) {
		if (consensusId == null) {
			PadFsLogger.log(LogLevel.ERROR,	"consensusId is null");
			return false;
		}

		this.currentProposerPendingOp = pendingOp;
		this.currentProposerConsGroupId = consensusId;
		return true;
	}

	/**
	 *  add the ExitMessage to notify the Acceptor that it must stop its execution 
	 *  and propagate the notification to completeOp Thread
	 *  
	 *  */
	/*public void stopAcceptor() {
		this.acceptThread.inConsMsg.add(new ExitMessage());
	}*/

	public void deleteActualProposerConsensusGroup() {
		this.currentProposerConsGroupId = null;

	}

	/*
	 * questi metodi vengono utilizzati solo dall'acceptor
	 */
	public synchronized Long getCurrentProposerConsGroupId() {
		return currentProposerConsGroupId;
	}
	
	
	public synchronized JobOperation getCurrentProposerPendingOp() {
		return currentProposerPendingOp;
	}
	
	
	public void deleteActualProposerPendingOp() {
		this.currentProposerPendingOp = null;

	}
	
	
	
	
	
	

	private class Notification {
		private ProposalNumber propNum;
		private String idOp;
		private boolean isRejected;

		/*
		 * set isRejected to true if the server has received a majority of negative replies
		 */
		public Notification(ProposalNumber p, String idOp, boolean isRejected) {
			this.propNum = p;
			this.idOp = idOp;
			this.isRejected = isRejected;
		}

		public boolean isRejected() {
			return isRejected;
		}

		public ProposalNumber getProposalNumber() {
			return propNum;
		}
		
		public String getidOp() {
			return idOp;
		}

	}

}