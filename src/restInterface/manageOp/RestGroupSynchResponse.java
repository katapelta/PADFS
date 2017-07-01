package restInterface.manageOp;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jobManagement.jobOperation.JobOperation;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Constants.Rest.status;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class RestGroupSynchResponse extends RestManageOp {
	private Rest.status status;
	private List<JobOperation> jobOperations;
	private Rest.errors error = null;


	public RestGroupSynchResponse(@JsonProperty("status") Rest.status status,
								  @JsonProperty("jobOperations") List<Map<String,String>> jobOperations,
								  @JsonProperty("error") Rest.errors error){
		this.status 	= status;
		this.error  	= error;
		try{
			this.jobOperations = new  LinkedList<JobOperation>();
			for(int i = 0; i< jobOperations.size();i++){
				this.jobOperations.add(JobOperation.createFromJson(jobOperations.get(i).get("operation"), jobOperations.get(i).get("type")));
			}
		}
		catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR, "failed reading RestGroupSynchResponse");
			this.jobOperations = null;
		}
		
		
	}
	
	@JsonIgnore
	public RestGroupSynchResponse(List<JobOperation> jobList,status status , Rest.errors error) {
		this.status 	= status;
		this.error  	= error;
		this.jobOperations = jobList;
	}

	public Rest.errors getError(){
		return this.error;
	}
	
	public Rest.status getStatus(){
		return this.status;
	}

	public List<Map<String,String>> getJobOperations() {
		List<Map<String,String>> ret = new LinkedList<Map<String,String>>();
		for(int i = 0; i< jobOperations.size();i++){
			Map<String,String> m = new HashMap<>();
			m.put("operation", jobOperations.get(i).toJSON());
			m.put("type", jobOperations.get(i).getClass().getName());
			ret.add(m);
		}
		return ret;
	}
	
	@JsonIgnore
	public List<JobOperation> getJobOperationList() {
		return jobOperations;
	}

	
}
