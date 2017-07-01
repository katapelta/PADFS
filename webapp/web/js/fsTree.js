var FSTree = null;
var SLASH = "\/";

/**********************************************************************
  The elements of the tree
**********************************************************************/
var fsTree = function(path,status,isDir,label,defaultOpen,jsOnClick,username,password){
  this._path        = path;         // /<username>/<listOfFolder>
  this._status      = status;       // if enable or disable
  this._isDir       = isDir;        // true=is directory ; false=is file
  this._label       = label;        // the name that appear in the treeView
  this._jsOnClick   = jsOnClick;    // JS function to do onClick
  this._username    = username;     // username of the owner
  this._password    = password;     // password of the owner
  this._defaultOpen = defaultOpen;  // true=when load the open (if folder) ; false=close (if folder)
  this._subDir      = new subDir(); // the subdirectory that is the same structure as this with other files
}

function generateRoot(username,password){
  FSTree = new fsTree(SLASH,true,true,"PADFS",true,false,username,password); //create the ROOT directory on boot
}

/**
 * Generate a file element for the tree
 * @param  {String} path     destination path (complete path with it selfs: the name of the file)
 * @param  {String} username the owner of the element
 * @return {fsTree}          the element
 */
function createFile(path,label=null,username,password){
  if(label == null) label = extractNamePath(path);
  //       function(path,status,isDir,label,defaultOpen,jsOnClick,username,password)
  return new fsTree(path,true  ,false,label,false      ,null     ,username,password);
}
/**
 * Generate a directory element for the tree
 * @param  {String} path     destination path (complete path with it selfs)
 * @param  {String} username the owner of the element
 * @return {fsTree}          the element
 */
function createDir(path,onClick=true,username,password){
  var label = extractNamePath(path);
  //       function(path,status,isDir,label,defaultOpen,jsOnClick,username,password)
  return new fsTree(path,true  ,true ,label,true       ,onClick  ,username,password);
}

/*
 The array list that represent the tree
*/
var subDir = function(){
  this._subDir = {};
  this._nElem = 0;
}

subDir.prototype = {
  //add a NEW element in the subdirectory
  add : function(key, fsTree){
    this._nElem ++;
    this._subDir[key] = fsTree;
  },

  //retrieve the element with key
  get : function(key){
    if(this._checkIfKeyExists(key)){
      return this._subDir[key];
    }else{
      return null;
    }
  },

  //retrieve the element return it and remove
  remove : function(key){
    if(this._checkIfKeyExists(key)){
      var tmp = this._subDir[key];
      delete this._subDir[key];                       //TODO compatibility problem in IE6-IE8
      this._nElem--;
      return tmp;
    }else{
      return null;
    }
  },

  size : function(){
    return this._nElem;
  },

  // true=IF EXISTS ; false=IF NOT
  _checkIfKeyExists : function(key){
    if(typeof this._subDir[key] === 'undefined') { return false; }else{ return true; }
  }
}
/**********************************************************************/

/**********************************************************************
 TOOLS to modify the tree status
 - INSERT
 - REMOVE
 - FIND ELEMENT
 - FIND FATHER ELEMENT
**********************************************************************/
var TMP_split_path        = null;
var TMP_position_in_path  = 0;
var TMP_dad_path          = null; //differ from path in the parameter if is file
var TMP_partial_path      = null;
var TMP_name_path         = null;
var TMP_isUserDir         = false;

//reset the var when finish
function reset_TMP_var(){
  TMP_split_path        = null;
  TMP_position_in_path  = 0;
  TMP_dad_path          = null;
  TMP_partial_path      = null;
  TMP_name_path         = null;
  TMP_isUserDir         = false;
}

/**
 * Return an array with the path elements
 * /dir1/dir2/dir3 => ["dir1","dir2","dir3"]
 *
 * @param  {String} path the complete path
 * @return {String[]}    the array represent the elements of the path
 */
function splitPath(path){
  if(path[0]==SLASH)
    path = path.substring(1);
  return path.split(SLASH);
}

/**
 * Retrieve only the path of father
 * @param  {String} path the complete path
 * @return {String}      the substring of the original path
 */
function extractDADPath(path){
  var TMP_path = path.substring(0,path.lastIndexOf(SLASH));
  return (TMP_path.length == 0)?SLASH:TMP_path;
}

/**
 * Retrieve only directory or filename
 * @param  {String} path the complete path
 * @return {String}      the substring of the original path
 */
function extractNamePath(path){
  var tmp = path.substring(path.lastIndexOf(SLASH)+1,path.length);
  return (tmp.length == 0)?SLASH:tmp;
}

/**
 * Return the element in the path
 * @param  {fsTree} tree          the tree
 * @param  {String} path          the COMPLETE path of the element
 * @param  {String} username
 * @return {null}                 if not FOUND
 * @return {fsTree}               otherwise
 */
function findElement(tree, path, username){
  if(tree == null) return ;

  if( TMP_split_path==null ){                   //FIRST LOOP => initialization
    username      = (username != null && username!="")?username:"UNKNOWN";
    if(path[0] != SLASH) path = SLASH+path;
    path              = SLASH+username+path;
    TMP_split_path    = splitPath(path);
    TMP_dad_path      = extractDADPath(path);
    TMP_partial_path  = SLASH;
    TMP_name_path     = extractNamePath(path);
  }

  if( TMP_partial_path == TMP_dad_path ){     //PATH FOUND ==> add to subDir
    var tmpName = TMP_name_path;
    reset_TMP_var();
    return tree._subDir.get(tmpName);
  }else{
    if(TMP_partial_path == SLASH){ TMP_partial_path = ""; }

    if(tree._subDir.size() == 0){               //NOT FOUND
      reset_TMP_var();
      return null;
    }else{
      TMP_partial_path = TMP_partial_path+SLASH+TMP_split_path[TMP_position_in_path];
      return findElement(tree._subDir.get(TMP_split_path[TMP_position_in_path++]),path);
    }
  }
}

/**
 * Retrieve the dad of the element in the path
 * @param  {fsTree} tree         the tree
 * @param  {String} path         the COMPLETE path of the element
 * @param  {String} username
 * @return {null}                if not FOUND
 * @return {fsTree}              otherwise
 */
function findDadElement(tree, path, username){
  if(tree == null) return ;

  if( TMP_split_path==null ){                   //FIRST LOOP => initialization
    username      = (username != null && username!="")?username:"UNKNOWN";
    if(path[0] != SLASH) path = SLASH+path;
    path              = SLASH+username+path;
    TMP_split_path    = splitPath(path);
    TMP_dad_path      = extractDADPath(path);
    TMP_partial_path  = SLASH;
    TMP_name_path     = extractNamePath(path);
  }

  if( TMP_partial_path == TMP_dad_path ){     //PATH FOUND ==> add to subDir
    reset_TMP_var();
    return tree;
  }else{
    if(TMP_partial_path == SLASH){ TMP_partial_path = ""; }

    if(tree._subDir.size() == 0){               //NOT FOUND
      reset_TMP_var();
      return null;
    }else{
      TMP_partial_path = TMP_partial_path+SLASH+TMP_split_path[TMP_position_in_path];
      return findDadElement(tree._subDir.get(TMP_split_path[TMP_position_in_path++]),path);
    }
  }
}

/**
 * Insert a new element (dir or file in the tree)
 *
 * @param  {fsTree} [currentElem=FSTree] the position in the tree
 * @param  {int}    [i=0]                the position in the TMP path variables
 * @param  {String} path
 * @param  {String} key
 * @param  {fsTree} element
 *
 * @return {int}
 */
 function insertInTree(currentElem = FSTree,path,element){
   if( currentElem == null  ){  return null;  }

   if( TMP_split_path==null ){ //FIRST LOOP => initialization
     var username      = (element._username != null && element._username!="")?element._username:"UNKNOWN";
     if(path.length > 0 && path[0] != SLASH) path = SLASH+path;
     path              = SLASH+username+path;
     TMP_split_path    = splitPath(path);
     TMP_dad_path      = extractDADPath(path);
     TMP_name_path     = extractNamePath(path);
     TMP_partial_path  = SLASH;
     TMP_isUserDir     = true;
   }

   if( TMP_partial_path == TMP_dad_path ){                                 //PATH FOUND ==> add to subDir
     currentElem._subDir.add(TMP_name_path,element);
     var tmpName = TMP_name_path;
     reset_TMP_var();
     return currentElem._subDir.get(tmpName);
   }else{

     if(TMP_partial_path == SLASH){ TMP_partial_path = ""; }

     nextKey              = TMP_split_path[TMP_position_in_path];

     if(currentElem._subDir._checkIfKeyExists(nextKey)){                         //if current DIR is not the rightone scan
       TMP_partial_path    += SLASH+TMP_split_path[TMP_position_in_path];
       currentElem          = currentElem._subDir.get(nextKey);
       TMP_position_in_path++;
       insertInTree(currentElem,path,element);
     }else{                                                                  //if the DIR not exists create it
       var local_username    = element._username;
       var local_password    = element._password;
       var local_jsOpen      = true;
       var local_key_label   = TMP_split_path[TMP_position_in_path++];
       var local_path        = TMP_partial_path+SLASH+local_key_label;

       TMP_partial_path      = local_path;

       if(TMP_isUserDir){ //ONLY IN THE FIRST CASE WHEN CREATE THE USER DIRECTORY
         local_path = "/";
         TMP_isUserDir       = false;
       }

       //if key not exists create it and continue to go down

      //function(path,status,isDir,label,defaultOpen,jsOnClick,username,password)
       currentElem._subDir.add(local_key_label,
         new fsTree(local_path,true,true,local_key_label,true,local_jsOpen,local_username,local_password)
       );
       currentElem  = currentElem._subDir.get(local_key_label);


       insertInTree(currentElem,path,element);
     }
   }
 }


  function insertInTreeSHARE(currentElem = FSTree,path,element){
    if( currentElem == null  ){  return null;  }

    if( TMP_split_path==null ){ //FIRST LOOP => initialization
      var username      = (element._username != null && element._username!="")?element._username:"UNKNOWN";
      if(path.length > 0 && path[0] != SLASH) path = SLASH+path;
      path              = SLASH+"SHARE"+SLASH+username+path;
      TMP_split_path    = splitPath(path);
      TMP_dad_path      = extractDADPath(path);
      TMP_name_path     = extractNamePath(path);
      TMP_partial_path  = SLASH;
      TMP_isUserDir     = true;
    }

    if( TMP_partial_path == TMP_dad_path ){                                 //PATH FOUND ==> add to subDir
      currentElem._subDir.add(TMP_name_path,element);
      var tmpName = TMP_name_path;
      reset_TMP_var();
      return currentElem._subDir.get(tmpName);
    }else{

      if(TMP_partial_path == SLASH){ TMP_partial_path = ""; }

      nextKey              = TMP_split_path[TMP_position_in_path];

      if(currentElem._subDir._checkIfKeyExists(nextKey)){                         //if current DIR is not the rightone scan
        TMP_partial_path    += SLASH+TMP_split_path[TMP_position_in_path];
        currentElem          = currentElem._subDir.get(nextKey);
        TMP_position_in_path++;
        insertInTreeSHARE(currentElem,path,element);
      }else{                                                                  //if the DIR not exists create it
        var local_username    = element._username;
        var local_password    = element._password;
        var local_jsOpen      = true;
        var local_key_label   = TMP_split_path[TMP_position_in_path++];
        var local_path        = TMP_partial_path+SLASH+local_key_label;

        TMP_partial_path      = local_path;

        if(TMP_isUserDir){ //ONLY IN THE FIRST CASE WHEN CREATE THE USER DIRECTORY
          local_path = "/";
          TMP_isUserDir       = false;
        }

        //if key not exists create it and continue to go down

       //function(path,status,isDir,label,defaultOpen,jsOnClick,username,password)
        currentElem._subDir.add(local_key_label,
          new fsTree(local_path,true,true,local_key_label,true,local_jsOpen,local_username,local_password)
        );
        currentElem  = currentElem._subDir.get(local_key_label);


        insertInTreeSHARE(currentElem,path,element);
      }
    }
  }

/**
 * Remove element in the tree if exists
 * @param  {fsTree} [currentElem=FSTree] the tree
 * @param  {String} path                 the COMPLETE path of the element
 * @param  {String} username
 * @return {boolean} true                if deleted successfully
 * @return {boolean} false               if something wrong
 */
function removeInTree(currentElem = FSTree,path,username){
  var username      = (username != null && username!="")?username:"UNKNOWN";
  var tmpName       = extractNamePath(path);
  var dad           = findDadElement(currentElem, path, username);
  if(dad == null) return false;
  else{
    dad._subDir.remove(tmpName);
    return true;
  }
}
/**********************************************************************/

/**********************************************************************
  DEBUG FUNCTION
**********************************************************************/
var debug = function(){
  var TMP_DEBUG_currentPath = "";
}
debug.prototype = {
  reset_DEBUG_PRINT_VAR : function(){
    this.TMP_DEBUG_currentPath = "";
  },

  /**
   * Print all the tree elements with complete path IN CONSOLE
   *
   * @param  {fsTree} tree             the ROOT or a sub element
   * @param  {String} [currentPath=""] (optional) the current path
   */
  debugPrint : function(tree, currentPath=""){
    if(tree == null) return ;
    if(tree._subDir.size() == 0){
      msgTree(currentPath); //print the leaf of the tree
      return ;
    }else{


      var arr = tree._subDir._subDir;
      for (var key in arr){
        this.TMP_DEBUG_currentPath = currentPath +SLASH+key;
        //msgTree("key: "+key+" - path: "+TMP_DEBUG_currentPath)
        this.debugPrint(arr[key],this.TMP_DEBUG_currentPath); //scan the tree
      }
    }

    if(tree._label == "PADFS"){ //END OF THE LOOP
      this.reset_DEBUG_PRINT_VAR()
    }
  },

  /**
   * TEST FUNCTION FOR DEBUGGING THE TOOLS
   */
  test : function(){

    var username = "admin";
    var password = "admin";

    msgTree("----------------INSERT TEST-----------------------------");

    // /admin/test/pippo2/pippo
    path = SLASH+"test"+SLASH+"pippo2"+SLASH+"pippo";
    insertInTree(FSTree,path,createDir(path,null,username,password));
    msgTree("INSERT: "+path+" FOR THE USER: "+username);

    // /admin/test/pippo2/pippo2
    path = SLASH+"test"+SLASH+"pippo2"+SLASH+"pippo2";
    insertInTree(FSTree,path,createDir(path,null,username,password));
    msgTree("INSERT: "+path+" FOR THE USER: "+username);

    // /admin/test/pippo2/pippo2.txt
    path = SLASH+"test"+SLASH+"pippo2"+SLASH+"pippo2.txt";
    insertInTree(FSTree,path,createFile(path,"pippo2.txt",username,password));
    msgTree("INSERT: "+path+" FOR THE USER: "+username);

    // /admin/test/pippo/pippo2.txt
    path = SLASH+"test"+SLASH+"pippo"+SLASH+"pippo2.txt";
    insertInTree(FSTree,path,createFile(path,"pippo2.txt",username,password));
    msgTree("INSERT: "+path+" FOR THE USER: "+username);

    // /admin/test/pippo/pippo2
    path = SLASH+"test"+SLASH+"pippo"+SLASH+"pippo2";
    insertInTree(FSTree,path,createDir(path,null,username,password));
    msgTree("INSERT: "+path+" FOR THE USER: "+username);

    // /admin/test/pippo2/pippo2
    path = SLASH+"test"+SLASH+"pippo2"+SLASH+"pippo2";
    insertInTree(FSTree,path,createDir(path,null,username,password));
    msgTree("INSERT: "+path+" FOR THE USER: "+username);

    msgTree("-----------------PRINT TEST----------------------------");

    msgTree("ALL ELEMENTS: ");
    this.debugPrint(FSTree);

    msgTree("-----------------FIND ELEMENT TEST---------------------");

    path = SLASH+"test"+SLASH+"pippo"+SLASH+"pippo2";
    msgTree("FIND ELEMENT: "+path+" \nRESULT: ");
    msgTree(findElement(FSTree,path,username));

    path = SLASH+"test"+SLASH+"pippo"+SLASH+"pippo2";
    msgTree("FIND FATHER ELEMENT: "+path+" \nRESULT: ");
    msgTree(findDadElement(FSTree,path,username));

    msgTree("-----------------REMOVE TEST---------------------------");
/*
    path = SLASH+"test"+SLASH+"pippo2"+SLASH+"pippo2";
    removeInTree(FSTree,path,username);
    msgTree("REMOVED: "+path+" FOR THE USER: "+username);

    path = SLASH+"test"+SLASH+"pippo"+SLASH+"pippo2";
    removeInTree(FSTree,path,"admin");
    msgTree("REMOVED: "+path+" FOR THE USER: "+username);
*/
    msgTree("------------------FIND ELEMENT TEST--------------------");

    path = SLASH+"test"+SLASH+"pippo"+SLASH+"pippo2";
    msgTree("FIND ELEMENT: "+path+" \nRESULT: ");
    msgTree(findElement(FSTree,path,username));

    path = SLASH+"test"+SLASH+"pippo"+SLASH+"pippo2";
    msgTree("FIND FATHER ELEMENT: "+path+" \nRESULT: ");
    msgTree(findDadElement(FSTree,path,username));

    msgTree("------------------PRINT TEST---------------------------");

    msgTree("ALL ELEMENTS: ");
    this.debugPrint(FSTree);

    msgTree("------------------END----------------------------------");

  }
}
function msgTree(data){
  console.log(data);
}
/**********************************************************************/

/**********************************************************************
  REST FUNCTION + creation element
**********************************************************************/
var View = function(username, password){
  this.TMP_partial_currentPath = "";
  this.username = username;
  this.password = password;
}

View.prototype = {
  resetAllVar: function(){
    this.TMP_partial_HTML        = "";
    this.TMP_partial_currentPath = "";
 },
 resetVar: function(){
   this.TMP_partial_currentPath = "";
 },

 appendToHTML: function(html){
   this.TMP_partial_HTML += html;
 },

 header: function(){
   this.appendToHTML("<div id='fsTree' class='treeviewContainer'> \n <div class=\"css-treeview\">");
 },

 foter: function(){
   this.appendToHTML("</div> \n </div>");
 },

 openDir: function(dirName,dirId, open, enable, jsOnClick, username, password, path){
   var open      = (open)?"checked=\"checked\""   :"";
   var enable    = (!enable)?"disabled=\"disabled\" ":"";
   var jsOnClick = (jsOnClick)?"onClick=\"listFS('"+username+"','"+password+"','"+path+"');\"":"";
   this.appendToHTML("<ul>\n"+
                  "<li><input type=\"checkbox\" "+open+" "+enable+" "+jsOnClick+" id=\""+dirId+"\" /><label for=\""+dirId+"\">"+dirName+"</label>\n"+
                      "\t<ul>\n"
                );
 },

 closeDir: function(){
   this.appendToHTML("\t</ul>\n</ul>");
 },

 appendFile: function(fileName){
    this.appendToHTML("<li><div>"+fileName+"</div></li>");
 },

 updateView: function(){
   this.resetAllVar();
   this.header();
   this.openDir("PADFS","id_PADFS",true, true, false, this.username,this.password,"/");
   this.scanFSTreeAndUpdate(FSTree);
   this.closeDir();
   this.foter();
   $("#treeFS").html(this.TMP_partial_HTML);
 },

 scanFSTreeAndUpdate: function(tree,currentPath=""){
   if(tree == null) return ;
   if(tree._subDir.size() == 0){
     //msgTree(currentPath); //print the leaf of the tree
     if(tree._isDir){
      /*  var key = tree._label;
         this.openDir(key,"id_"+key,true,true,"",this.username,this.password,tree._path);
         this.closeDir();*/
     }else{
         this.appendFile(tree._label);
     }
     return ;
   }else{
     var arr = tree._subDir._subDir;
     for (var key in arr){
       this.TMP_partial_currentPath = currentPath +SLASH+ key;
       //msgTree("key: "+key+" - path: "+TMP_DEBUG_currentPath)

       if(arr[key]._isDir){         //IS DIR
         var elem = arr[key];
         var jsOnClick = elem._jsOnClick;
         var path      = elem._path;
         var isOpen    = false;
         if(elem._subDir.size() != 0){ //if no elements in the subdir the folder must be close
            isOpen=true;
          }
          this.openDir(key,"id_"+key,isOpen,true,jsOnClick,this.username,this.password,path);
          if(isOpen)
            this.scanFSTreeAndUpdate(elem,this.TMP_partial_currentPath); //scan the tree
          this.closeDir();
       }else{                       //IS FILE
         this.appendFile(arr[key]._label);
       }
     }
   }

   if(tree._label == "PADFS"){ //END OF THE LOOP
     this.resetVar();
   }
 }

}

/**********************************************************************/
/*
function bootFSTREE_test(){
  generateRoot("admin","admin");
  var d = new debug()
  d.test();

  var v = new View("admin","admin")
  v.updateView();

}
*/
