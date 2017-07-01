var timeoutRetry    = 0; //manage the timeout retry
var timeoutMaxRetry = 2;
var timeoutRefresh  = 3000;
var timeoutRetray   = 3000;
var msgTime         = 3000;

var LOGIN_username = null;
var LOGIN_password = null;

var SERVER_userList = new Array();

var SLASH = "\/";

function bootManagement(){
  deactivateReplyDiv()
  updateTreeViewManagement();
  updateServerList();
}

function boot(){
  deactivateReplyDiv()
  generateRoot(LOGIN_username,LOGIN_password);
  listFS(LOGIN_username,LOGIN_password,SLASH);
  share(LOGIN_username,LOGIN_password);
}

function share(username, password){
        var dirFile = null;
        var v = new View(username, password);
    var urlSend = "/getSharedFile/"+username+"/"+password+"/";
        $.ajax({
                type: "GET",
                url: urlSend,
                contentType: "application/json; charset=utf-8",
                dataType: "json",
                success: function(data, statusText, resObject){
                  if(data["error"] == null){
                    if(data['fileList']!=null && data['fileList']!=""){
                      var arr = data['fileList'];
                     	for(var key in arr){
                          var elem        = arr[key];
                          var retPath     = elem['path'];
                          var isDirectory = elem['directory'];
                          var size        = elem['size'];
                          var dateTime    = elem['dateTime'];

                          var nameOwner   = elem['nameOwner'];
                          var passOwner   = "";

                          if(isDirectory=="true"){
                            dirFile       = createDir(retPath,true,nameOwner,passOwner);
                          }else{
                            var name = extractNamePath(retPath);
                            dirFile       = createFile(retPath,name+" - "+size+" Kb - "+dateTime,nameOwner,passOwner);
                          }
                          insertInTreeSHARE(FSTree,retPath,dirFile);
                        }
                  	}
                  }else{
                    msg(1,"ERROR RETRIEVE LIST: <br>"+data["errorDescription"])
                  }
                  v.updateView()
                },
                failure: function(errMsg, statusText, resObject) {
                    msg(1,errMsg)
                }
        });
}

/**
 * Function that retrieve the list of the files for the username and path
 * It write directly in the div 'treeFS'
 *
 * @param username
 * @param password
 * @param path
 */
function listFS(username, password, path){
    var dirFile = null;
    var v = new View(username, password);
    //clean path
    if(path.length==0 || path == null || (path.length == 1 && path[0] == SLASH) ) path = "";
    if(path.length > 1 && path[0]==SLASH) path = path.substring(1,path.length);

    var urlSend = "/list/"+username+"/"+password+"/"+username+"/"+path;
    $.ajax({
            type: "GET",
            url: urlSend,
            contentType: "application/json; charset=utf-8",
            dataType: "json",
            success: function(data, statusText, resObject){
              if(data["error"] == null){
                if(data['pathList']!=null && data['pathList']!=""){
                  var arr = data['pathList'];
                 		for(var key in arr){
                      var elem        = arr[key];
                      var retPath     = elem['path'];
                      var isDirectory = elem['isDirectory'];
                      var size        = elem['size'];
                      var dateTime    = elem['dateTime'];

                      if(isDirectory=="true"){
                        dirFile       = createDir(retPath,true,username,password);
                      }else{
                        var name = extractNamePath(retPath);
                        dirFile       = createFile(retPath,name+" - "+size+" Kb - "+dateTime,username,password);
                      }
                      insertInTree(FSTree,retPath,dirFile);
                    }
              	}else{
                  if(findDadElement(FSTree,"/"+username,username) == null){
                    insertInTree(FSTree,"",createDir("/",true,username,password));
                  }else{
                    msg(2,  "NO SUBDIRECTORY FOUND!");
                  }
                }
              }else{
                msg(1,"ERROR RETRIEVE LIST: <br>"+data["errorDescription"])
              }
              v.updateView()
            },
            failure: function(errMsg, statusText, resObject) {
                msg(1,errMsg)
            }
    });
}

/**
 * update the data in the TreeView
 */
function updateTreeViewManagement(){
  generateRoot(LOGIN_username,LOGIN_password);
  getUserList();
}

function updateTreeView(){
  boot();
}

function updateServerList(){
    $.ajax({
            type: "GET",
            url: "/updateserverlist",
            dataType: "html",
            success: function(data, statusText, resObject){
                $("#serverliststatus").html(data);
            },
            failure: function(errMsg, statusText, resObject) {
                msg(1,errMsg+"<br>"+statusText)
            }
    });
}

function getUserList(){
    if(LOGIN_password == null){
      msg(1,"RETRIEVE USER LIST ERROR! check login password");
      return;
    }

    $.ajax({
        type: "GET",
        url: "/userList/"+LOGIN_password,
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function(data, statusText, resObject)
        {
        	if(data['listUsers']!=null && data['listUsers']!="")
          {
            SERVER_userList = new Array();
            var arr = data['listUsers'];
           		for(var key in arr){
                elem = arr[key];
                if(elem != null){
                  tmp = new Array();
                  tmp.push(elem['id'])
                  tmp.push(elem['user']);
                  tmp.push(elem['password']);
                  SERVER_userList[elem['user']]=tmp;

                  listFS(elem['user'],elem['password'],SLASH);
                }
              }
        	}

        },
        failure: function(errMsg, statusText, resObject)
        {
            msg(1,errMsg)
        }
    });
}





/**
 * Collapse the div if not
 *
 * @param areaName the name of the div to collapse
 */
function toggleArea(areaName){
    var $this = $("#"+areaName);
    if($this.is(":hidden")){
        collapseArea(areaName)
    }
}

/**
 * Force to collapse the div
 *
 * @param areaName the name of the div to collapse
 */
function collapseArea(areaName){
    var $this = $("#"+areaName);
    $this.collapse('toggle');
}


/**
 * This function is used in the combobox area to open the link
 *
 * @param link the page to retrieve and append to the div 'areaName'
 * @param areaName the name of the div to collapse
 */
function openLink(link, areaName){
    timeoutRetry = 0; //another page is required i forgot the past
    deactivateReplyDiv()
    $.ajax({
        type: "GET",
        url: link,
        dataType: "html",
        success: function(data, statusText, resObject){
            var $this = $("#"+areaName);
            if(! $this.is(":hidden") ){ $this.collapse(); }
            $("#"+areaName).html(data);
            toggleArea(areaName);
            scrollToElement("#"+areaName);
        },
        failure: function(errMsg, statusText, resObject) {
            msg(1,errMsg)
        }
    });
}

/**
 * This is a function to validate the form data
 *
 * @param data the string represent the data from the form (ex. var1=xxx&var2=yyyy... )
 * @param stringDescription the string represent the required fields
 *                              (ex. 1,0,...
 *                                    in this examples var1 is required var2 no
 *                              )
 *                              1 = required
 *                              0 = not required
 * @return 1 if data is null or EMPTY
 * @return 2 if stringDescription is null or EMPTY
 * @return 3 if everthing is ok
 * @return -x if the fields x in the form is EMPTY and is required
 */
function checkDataSerialized(data, stringDescription){
    data=data.trim();
    stringDescription = stringDescription.trim();
    if(!data || data==null || data == ""){
        return 1;
    }

    if(!stringDescription || stringDescription==null || stringDescription == ""){
        return 2;
    }

    stringDescription = stringDescription.split(',');

    var element = data.split("&");
    for(i=0; i<element.length; i++){
        var subE = element[i].split("=");
        if( stringDescription[i] != 0 ){
            if(!subE[1].trim()){
                return -1*i;
            }
        }
    }

    return 3;
}

/*DEPRECATED
function checkIfUpdateTreeView(url){
  switch (url) {
    case '/putAction':
    case '/redirectIntermediatePage/2/':
    case '/redirectIntermediatePage/3/':
    case '/redirectIntermediatePage/4/':
    case '/redirectIntermediatePage/5/':
    case '/redirectIntermediatePage/6/':
      updateTreeView();
      break;
    default:
      return;
  }
}*/

function toggleReply(){
  toggleDiv("reply_toggleArea");
}

function activateReplyDiv(){
  $("#reply_toggleArea").show();
}


function deactivateReplyDiv(){
  $("#reply_toggleArea").hide();
}

function toggleDiv(divName){
    if($("#"+divName).length)
        $("#"+divName).toggle();
}



function toggleDisableDiv(divName){
    if($("#"+divName).length)
        $("#"+divName).toggleClass("disabledbutton");
}

function toggleDisableTreeFS(){
    toggleDisableDiv("treeFS");
}

function toggleDisableAreaAndButton(){
    toggleDisableDiv("pageArea");
    toggleLoadingOnDiv("pageArea");
}

function toggleLoadingOnDiv(divName){
    if($("#loading_"+divName).length){
        $("#loading_"+divName).remove();
    }else{
        $("#"+divName).append("<div id='loading_"+divName+"' class='loading'><img src='web/img/loading.gif' ></div>")
    }
}

function openInNewTab(url) {
  var win = window.open(url, '_blank');
  win.focus();
}

function scrollToElement (selector) {
  $('html, body').animate({
    scrollTop: $(selector).offset().top
  }, 2000);
};

function endLocalJSON(url){
    timeoutRetry = 0;
    setTimeout(function(){
     updateTreeViewManagement();
     toggleDisableTreeFS();
     toggleDisableAreaAndButton();
    }, timeoutRefresh); //wait for propagation of the op. to all servers
}


/**
 * Redirect with JQUERY the form data to the page (url) for the action required
 * @param  {String} url               the url of the page to send the data
 * @param  {String} stringDescription the fields of the form to check. the string represent the
 *                                    required fields and the not required.
 *                                    0 => not required fields
 *                                    1 => required fields
 *                                    ex. 1,0,... => input 1 is required input 2 not
 * @param {String}  fileUploadName    the name of the field with the file (special case of /put) default null
 * @return {boolean}              false to lock the submit function by browser and manage everthing
 *                                in asynchronous mode by JQUERY
 */
 function submitLocalJSON(url, stringDescription){
     //DEBUG console.log("timeoutRetry: "+timeoutRetry);
     //DEBUG console.log("submitLocalJSON('"+url+"','"+stringDescription+"')")
     deactivateReplyDiv()
     var data = $('form').serialize(); //retrieve only the field in the form that are input or select
     //var url  = $('form').attr('action');

     if( (data == null || data == "") || (url == null || url == "") ){
         msg(1,"INPUT DATA MISSING OR DESTINATION PAGE MISSING!");
         return ;
     }
     var elementError=checkDataSerialized(data,stringDescription);
     if( elementError == 3 ){
         data = new FormData($('form')[0]); //native methot to retrive ALL the form element also file

         /** DEBUG ***
         for (var pair of data.entries()) {

             console.log(pair);
         }
         */
         if(timeoutRetry==0){
            toggleDisableTreeFS()
            toggleDisableAreaAndButton()
         }

         //SPECIAL CASE
         if(url == "/redirectIntermediatePage/1/"){ //GET
           endLocalJSON(url);
           data = $('form').serialize();
           openInNewTab("/redirectIntermediatePage/1/?"+data)
         }else{ //OTHERS
           $.ajax({
               type: "POST",
               url: url,
               data: data,
               dataType: "html",
               cache: false,
               contentType: false,
               processData: false,
               timeout: 15000, //15 sec.
               success: function(data, statusText, resObject){

                   var jsonData = $.parseJSON(data);
                   if(jsonData.status == "error"){
                       msg(1,"ERROR on operation: <b>"+jsonData.error+"</b>");
                   }else{
                       var retVal = "";
                       $.each(jsonData,function(key,value){
                           if(
                               key!="errorDescription" &&
                               key!="error" &&
                               key!="status"
                           ){
                               retVal +=  "- " + key+" = "+value+"<br>  ";
                           }
                       })

                       $("#reply_toggleArea").html("<h3>REPLY:</h3> <br>"+retVal);
                       activateReplyDiv()
                       scrollToElement("#reply_toggleArea");
                       msg(2, "Operation DONE!")
                   }
                   endLocalJSON(url); //reenable the treeview only if recieve a success reply or if i reach the max retry number


               },
               error: function( objAJAXRequest, strError ){
                  if(strError=="timeout"){
                      msg(1,"SORRY REQUEST TIMEOUT! <br> num. RETRY: "+timeoutRetry);
                      if(timeoutRetry < timeoutMaxRetry){
                          timeoutRetry ++;
                          setTimeout(function(){ submitLocalJSON(url, stringDescription) }, timeoutRetray);
                      }else{
                          endLocalJSON(url); //reenable the treeview only if recieve a success reply or if i reach the max retry number
                      }
                  }else{
                      msg(1,strError);
                      endLocalJSON(url); //reenable the treeview only if recieve a success reply or if i reach the max retry number
                  }

               }
           });
         }

     }else{
         if(elementError > 0){
             msg(1,"ERROR on input data elment NULL or EMPTY" );
         }
         elementError = (elementError*-1)+1;
         msg(1,"ERROR on input data elment: <b>"+elementError+"</b>" );
         endLocalJSON(url); //reenable the treeview only if recieve a success reply or if i reach the max retry number
     }

     return false;
 }

function checkLogin(url){
    var data = $('form').serialize(); //retrieve only the field in the form that are input or select
    //console.log(url);
    if( (data == null || data == "") || (url == null || url == "") ){
     msg(1,"INPUT DATA MISSING OR DESTINATION PAGE MISSING!");
     return ;
    }
    //console.log(data)
    var elementError=checkDataSerialized(data,"1,1");
    if( elementError == 3 ){

        //SAVE username & password in the session
        $("form :input").each(function(){
            var elem = $(this);
            if(elem.attr("name") == "username")
              LOGIN_username = elem.val()

            if(elem.attr("name") == "password")
              LOGIN_password = elem.val()
        });


        $.ajax({
         type: "POST",
         url: url,
         data: data,
         dataType: "html",
         cache: false,
         timeout: 10000, //10 sec.
         success: function(data, statusText, resObject){
            if(data!="-1"){
                msg(3, "LOGIN SUCCESS!");
                $("#page").html(data);
            }else{
                msg(1, "LOGIN ERROR CHECK DATA");
            }
         },
         error: function( objAJAXRequest, strError ){
            if(strError=="timeout"){
                msg(1,"SORRY REQUEST TIMEOUT! RETRAY!");
            }else{
                msg(1,strError);
            }
         }
        });

        }else{
            if(elementError > 0){
                msg(1,"ERROR on input data elment NULL or EMPTY" );
            }
            elementError = (elementError*-1)+1;
            msg(1,"ERROR on input data elment: <b>"+elementError+"</b>" );
        }

        return false;
}

function checkLoginUser(){
    return checkLogin("/loginUser", "USER PAGE");
}

function checkAdminLogin(){
    return checkLogin("/loginManager", "MANAGER PAGE");
}


/**
 * Message alert messaging that required BOOTSTRAP
 * The message appear for 3000 milliseconds (3 sec.) and automatically removed
 *
 * @param  {String} type 0: DEBUG
 *                       1: ERROR
 *                       2: INFO
 *                       3: SUCCESS
 * @param  {String} msg  The text of the message
 */
function msg(type, msg){
    //console.log("DEBUG: "+msg);
    switch(type){
        case 0: //debug
            $("<div class=\"alert alert-warning\" role=\"warning\">"+msg+"</div>").appendTo('body').delay(msgTime).queue(function() { $(this).remove(); });
            break;
        case 1: //error
            $("<div class=\"alert alert-danger\" role=\"error\">"+msg+"</div>").appendTo('body').delay(msgTime).queue(function() { $(this).remove(); });
            break;
        case 3: //success
            $("<div class=\"alert alert-success\" role=\"success\">"+msg+"</div>").appendTo('body').delay(msgTime).queue(function() { $(this).remove(); });
            break;
        case 2: //normal
        default:
            $("<div class=\"alert alert-info\" role=\"info\">"+msg+"</div>").appendTo('body').delay(msgTime).queue(function() { $(this).remove(); });
    }
}
