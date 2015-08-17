package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {


    public static Node predecessor;

    public static Node successor;

    public static String localPort;

    public static Node localNode;

    static List<Node> chordNodes;

    Uri providerUri;

    String queryResponseRcvd;

    String globalResponse;

    static final int SERVER_PORT = 10000;

    static final String TAG = SimpleDhtProvider.class.getSimpleName();

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.v(TAG,"delete method called");
        String key = selection;
        if(CommonConstants.QUERY_LOCAL_ALL.equals(key)){
            deleteLocalFiles();
        }else if(CommonConstants.STRING_QUERY_GLOBAL_ALL.equals(key)){
            deleteLocalFiles();
            StringBuilder sb = new StringBuilder();
            sb.append(CommonConstants.MSG_TYPE_DELETE).append(CommonConstants.HASH_SEP)
                    .append(localPort).append(CommonConstants.HASH_SEP)
                    .append(CommonConstants.STRING_QUERY_GLOBAL_ALL);
            String message=sb.toString();
            Log.v(TAG,"delete:: Forwarding delete global "+message+" to successor");
            CommonUtil.unicastMessage(message, successor.getPortNum());
        }else{
            if(lookup(key)){
                deleteSingleLocalFile(key);
            }else {
                StringBuilder sb = new StringBuilder();
                sb.append(CommonConstants.MSG_TYPE_DELETE).append(CommonConstants.HASH_SEP)
                        .append(localPort).append(CommonConstants.HASH_SEP)
                        .append(key);
                String message=sb.toString();
                Log.v(TAG,"delete:: Forwarding delete specific key "+message+" to successor");
                CommonUtil.unicastMessage(message, successor.getPortNum());
            }
        }

        return 0;
    }

    public void deleteLocalFiles() {
        File filesDir=getContext().getFilesDir();

        for (File file : filesDir.listFiles()) {
            boolean flag = getContext().deleteFile(file.getName());
            Log.v(TAG,"Returned flag "+flag);
        }
    }

    public void deleteSingleLocalFile(String key) {
        String fileName = key;
        File file=new File(fileName);
        boolean flag = getContext().deleteFile(fileName);
        Log.v(TAG,"Returned flag "+flag);
        Log.v(TAG,"Deleted File "+fileName);
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {


        String key = (String)values.get(CommonConstants.KEY_FIELD);
        String value = (String)values.get(CommonConstants.VALUE_FIELD);
        //Log.v(TAG,"Insert method called for key:"+key +"value "+value);


        if(lookup(key)){
            String filename = key;
            String string = value;

            FileOutputStream outputStream;

            try {
                outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
            Log.v(TAG,"insert:: "+filename+"value "+string+" inserted");
        }
        /**
         * Forward the key to the successor
         */
        else{
           Log.v(TAG,"Forwarding key "+key +" value "+value+" to successor "+successor.getPortNum());
           StringBuilder sb = new StringBuilder();
           sb.append(CommonConstants.MSG_TYPE_INSERT).append(CommonConstants.HASH_SEP)
              .append(key).append(CommonConstants.HASH_SEP)
              .append(value);
           String message=sb.toString();
           CommonUtil.unicastMessage(message, successor.getPortNum());
        }


        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {

        Log.v(TAG,"onCreate:: Oncreate started");

        providerUri = Uri.parse("content://" + "edu.buffalo.cse.cse486586.simpledht.provider"
                + "/edu.buffalo.cse.cse486586.simpledht.SimpleDhtProvider");

        chordNodes = new ArrayList<Node>();
        /**
         * Calculate the local port number
         */
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        localPort = String.valueOf((Integer.parseInt(portStr) * 2));



        localNode = new Node(localPort,convertPortNumToHashVal(localPort));
        predecessor = new Node(localPort,convertPortNumToHashVal(localPort));
        successor = new Node(localPort,convertPortNumToHashVal(localPort));

         /*
         * Create a server socket as well as a thread (AsyncTask) that listens on the server
         * port.
         */

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }

        Log.v(TAG,"onCreate:: Sending node Join Message");
        /**
         * Send node join request
         */
        String nodeJoinReqMsg = CommonConstants.MSG_TYPE_NODE_JOIN_REQ+CommonConstants.HASH_SEP+
                                 localPort;
        String destPort = "11108";
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,nodeJoinReqMsg, destPort);

        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        //Log.v(TAG,"Query method called for key "+selection);
        MatrixCursor cursor=null;
        String key=selection;
        synchronized (this){
            if(CommonConstants.QUERY_LOCAL_ALL.equals(key)){
                cursor=handleLocalAllQuery();
            }else if(CommonConstants.STRING_QUERY_GLOBAL_ALL.equals(key)){
                cursor=handleGlobalAllQuery();
            }else{
                cursor=handleLocalQuery(key);
            }
        }

        return cursor;
    }

    protected MatrixCursor handleLocalQuery(String key){
        Log.v(TAG,"handleLocalQuery method called for key "+key);
        String string = "";
        String filename = null;

        if(lookup(key)){
            Log.v(TAG,"handleLocalQuery:: key "+key+" found");
            filename = key;
            FileInputStream inputStream;
            /**
             * Read file from the internal storage
             */
            try {
                /**
                 * Open the file
                 */
                inputStream = getContext().openFileInput(filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line=null;
                /**
                 * Read line by line
                 */
                while((line=reader.readLine())!=null){
                    string = string+line;
                }
                /**
                 * Close resources
                 */
                inputStream.close();
                reader.close();
            }catch (Exception e) {
                e.printStackTrace();
                Log.v("query", "File read failed");
            }
        }

        else{
            StringBuilder sb = new StringBuilder();
            sb.append(CommonConstants.MSG_TYPE_QUERY).append(CommonConstants.HASH_SEP)
                    .append(localPort).append(CommonConstants.HASH_SEP)
                    .append(key);
            String message=sb.toString();
            Log.v(TAG,"Forwarding query message "+message+" to successor "+successor.getPortNum());
            CommonUtil.unicastMessage(message, successor.getPortNum());
            while(queryResponseRcvd==null){
            }
            Log.v(TAG,"Response received in query method "+queryResponseRcvd);
            string = queryResponseRcvd;
            queryResponseRcvd = null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{CommonConstants.KEY_FIELD,CommonConstants.VALUE_FIELD});
        MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
        Log.v(TAG,"Adding key "+key+" value "+string+" in response");
        rowBuilder.add(CommonConstants.KEY_FIELD, key);
        rowBuilder.add(CommonConstants.VALUE_FIELD, string);
        return cursor;
    }

    protected MatrixCursor handleLocalAllQuery(){

        MatrixCursor cursor = new MatrixCursor(new String[]{CommonConstants.KEY_FIELD,CommonConstants.VALUE_FIELD});
        File filesDir=getContext().getFilesDir();
        FileInputStream inputStream;
        try {
            for (File file : filesDir.listFiles()) {
                String filename=null;
                filename = file.getName();
                inputStream = getContext().openFileInput(filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                String string="";
                while ((line = reader.readLine()) != null) {
                    string = string + line;
                }

                inputStream.close();
                reader.close();

                MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
                rowBuilder.add(CommonConstants.KEY_FIELD, filename);
                rowBuilder.add(CommonConstants.VALUE_FIELD, string);
            }
        }catch(Exception e){
            e.printStackTrace();
            Log.v("query", "File read failed");
        }
        return cursor;
    }


    protected MatrixCursor handleGlobalAllQuery(){

        MatrixCursor cursor = new MatrixCursor(new String[]{CommonConstants.KEY_FIELD,CommonConstants.VALUE_FIELD});
        StringBuilder sb = new StringBuilder();
        sb.append(CommonConstants.MSG_TYPE_GLOBAL_QUERY).append(CommonConstants.HASH_SEP)
                .append(localPort).append(CommonConstants.HASH_SEP);
        String message=sb.toString();
        String msg = constructLocalDump(message);
        Log.v(TAG,"Message sent for global query "+msg);
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg, successor.getPortNum());
        while(globalResponse==null){

        }
        String [] msgArr = globalResponse.split(CommonConstants.HASH_SEP);
        for(int i=2;i<msgArr.length;i++){
             String keyVal = msgArr[i];
             String [] keyValArr = keyVal.split("\\|");
             String key = keyValArr[0];
             String val = keyValArr[1];
             MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
             rowBuilder.add(CommonConstants.KEY_FIELD, key);
             rowBuilder.add(CommonConstants.VALUE_FIELD, val);
        }

        Log.v(TAG,"Response received from * :"+globalResponse);
        return cursor;
    }

    protected String constructLocalDump(String param){
        StringBuilder sb = new StringBuilder(param);
        String filename=null;
        FileInputStream inputStream;
        String string="";
        File filesDir=getContext().getFilesDir();
        try {
            for (File file : filesDir.listFiles()) {
                filename = file.getName();
                inputStream = getContext().openFileInput(filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                string = "";
                while ((line = reader.readLine()) != null) {
                    string = string + line;
                }

                inputStream.close();
                reader.close();
                sb.append(filename).append(CommonConstants.PIPE_SEP).append(string);
                sb.append(CommonConstants.HASH_SEP);
            }
        }catch(Exception e){
            e.printStackTrace();
            Log.v("query", "File read failed");
        }
        return sb.toString();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    protected String convertPortNumToHashVal(String portNum){
        //Log.v(TAG,"String port num "+portNum);
        Integer portNumInt = Integer.parseInt(portNum);
        //Log.v(TAG,"Integer port num "+portNumInt);
        String hashParam = String.valueOf(portNumInt / 2);
        String hashedKey=null;
        try{
            hashedKey = genHash(hashParam);
        }catch(NoSuchAlgorithmException e){
            Log.e(TAG, "handleQueryMessage:: SHA-1 func not found");
            e.printStackTrace();
        }
        return hashedKey;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    /**
     * Returns true if the hashed key maps to this node, else returns false
     * @param id
     * @return
     */
    public Boolean lookup(String id){
        Boolean flag = false;
        String idHash=null,predecessorNodeHash,localNodeHash,successorNodeHash;
        try{
            idHash = genHash(id);
        }catch(NoSuchAlgorithmException e){
            Log.e(TAG,"insert:: SHA-1 func not found");
            e.printStackTrace();
        }
        localNodeHash = convertPortNumToHashVal(localPort);
        predecessorNodeHash = convertPortNumToHashVal(predecessor.getPortNum());
        successorNodeHash = convertPortNumToHashVal(successor.getPortNum());


        //Log.v(TAG,"Predecessor Node hash: " +predecessorNodeHash);
        //Log.v(TAG,"Local Node hash: " +localNodeHash);
        //Log.v(TAG,"Successor Node hash: " +successorNodeHash);
        //Log.v(TAG,"Data hash : " +idHash);

        Boolean isDataLtEqLocal=CommonUtil.isLessThanEqual(idHash,localNodeHash);
        Boolean isDataLtEqPred=CommonUtil.isLessThanEqual(idHash,predecessorNodeHash);
        Boolean isPredGtThanLocal=!CommonUtil.isLessThanEqual(predecessorNodeHash,localNodeHash);

        //Log.v(TAG,"Data less than local flag: " +isDataLtEqLocal);
        //Log.v(TAG,"Data less than predecessor flag: " +isDataLtEqPred);
        //Log.v(TAG,"Predecessor greater than local flag: " +isPredGtThanLocal);

        if(localNodeHash.equals(predecessorNodeHash) && localNodeHash.equals(successorNodeHash)){
            //Log.v(TAG,"Only 1 node in the system");
            flag = true;
        }else if(isDataLtEqLocal && !isDataLtEqPred ){
            //Log.v(TAG,"Data is between predecessor and local");
            flag = true;
        }else if(isPredGtThanLocal){
               //Log.v(TAG,"Predecessor is greater than local");
               if(isDataLtEqLocal && isDataLtEqPred){
                   //Log.v(TAG,"Data is less than both predecessor and local");
                   flag = true;
               }
               else if(!isDataLtEqLocal && !isDataLtEqPred){
                   //Log.v(TAG,"Data is greater than both predecessor and local");
                   flag = true;
               }
        }


        return flag;
    }


    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever the 'Send'
     * Button is clicked
     * @author pnandi
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {


            String destPort = msgs[1];
            String msgToSend = msgs[0];
            CommonUtil.unicastMessage(msgToSend, destPort);
            return null;
        }

    }

    /***
     * HandleMessageTask is an AsyncTask that should handle the string received by the server
     * It is created by ClientTask.executeOnExecutor() call whenever the 'Send'
     * Button is clicked
     * @author pnandi
     *
     */
    private class HandleMessageTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String message = msgs[0];
            String msgArr[]=message.split(CommonConstants.HASH_SEP);
            String msgType = msgArr[0];
            if(msgType.equals(CommonConstants.MSG_TYPE_NODE_JOIN_REQ)){
                String newPort = msgArr[1];

                Node node = new Node(newPort,convertPortNumToHashVal(newPort));
                chordNodes.add(node);

                //Log.v(TAG,"ServerTask::Received node join request from" +newPort);
                CommonUtil.sendPredSuccessorMsg(chordNodes);
                //Log.v(TAG,"ServerTask::Sending predecessor successor message");

            }else if(msgType.equals(CommonConstants.MSG_TYPE_PRED_SUCC)){
                handlePredSuccessorMessage(message);
            }else if(msgType.equals(CommonConstants.MSG_TYPE_INSERT)){
                handleInsertMessage(message);
            }else if(msgType.equals(CommonConstants.MSG_TYPE_QUERY)){
                Log.v(TAG,"Received message type "+CommonConstants.MSG_TYPE_QUERY);
                handleQueryMessage(message);
            }else if(msgType.equals(CommonConstants.MSG_TYPE_QUERY_RESP)){
                Log.v(TAG,"Received message type "+CommonConstants.MSG_TYPE_QUERY_RESP);
                handleQueryRespMessage(message);
            }else if(msgType.equals(CommonConstants.MSG_TYPE_GLOBAL_QUERY)){
                Log.v(TAG,"Received message type "+CommonConstants.MSG_TYPE_GLOBAL_QUERY);
                handleGlobalQueryRcvdMessage(message);
            }else if(msgType.equals(CommonConstants.MSG_TYPE_DELETE)){
                Log.v(TAG,"Received message type "+CommonConstants.MSG_TYPE_DELETE);
                handleDeleteMessage(message);
            }

            return null;
        }


        /**
         * Called by all the receiving nodes
         * @param message
         */
        protected void handlePredSuccessorMessage(String message){
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String predecessorPort = msgArr[1];
            String successorPort = msgArr[2];

            predecessor = new Node(predecessorPort,convertPortNumToHashVal(predecessorPort));
            successor = new Node(successorPort,convertPortNumToHashVal(successorPort));
            //Log.v(TAG,"Predecessor of "+localPort+": "+predecessor.getPortNum()+"#"+predecessor.getHashVal());
            //Log.v(TAG,"Local port of "+localPort+": "+localNode.getPortNum()+"#"+localNode.getHashVal());
            //Log.v(TAG,"Successor of  "+localPort+": "+successor.getPortNum()+"#"+successor.getHashVal());

        }

        /**
         * Called by all the receiving nodes
         * @param message
         */
        protected void handleInsertMessage(String message){
            // Log.v(TAG,"Insert message received "+ message);
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String key = msgArr[1];
            String value = msgArr[2];
            ContentValues keyValueToInsert = new ContentValues();
            keyValueToInsert.put(CommonConstants.KEY_FIELD,key);
            keyValueToInsert.put(CommonConstants.VALUE_FIELD,value);

            Uri newUri = getContext().getContentResolver().insert(
                    providerUri,    // assume we already created a Uri object with our provider URI
                    keyValueToInsert
            );

            insert(providerUri,keyValueToInsert);
        }

        /**
         * Called by all the receiving nodes
         * @param message
         */
        protected void handleQueryMessage(String message){
            //Log.v(TAG,"Inside handleQueryMessage");
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String senderPort = msgArr[1];
            String key = msgArr[2];
            String string = "";
            String filename = null;
            if(lookup(key)){
                Log.v(TAG,"key "+key+" found");
                FileInputStream inputStream;
                try {

                    inputStream = getContext().openFileInput(key);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line = null;

                    while ((line = reader.readLine()) != null) {
                        string = string + line;
                    }

                }catch(Exception e){
                    Log.e(TAG,"FileNotFound in handleQueryMessage()");
                }
                StringBuilder sb = new StringBuilder();
                sb.append(CommonConstants.MSG_TYPE_QUERY_RESP).append(CommonConstants.HASH_SEP)
                        .append(string);
                String msgToSend=sb.toString();
                CommonUtil.unicastMessage(msgToSend, senderPort);

            }else{
                Log.v(TAG,key+ " not present. Forwarding request to successor "+successor.getPortNum());
                CommonUtil.unicastMessage(message,successor.getPortNum());
            }

        }

        /* Called on receipt of a response
        * @param message
        */
        protected void handleQueryRespMessage(String message){
            //Log.v(TAG,"handleQueryRespMessage():: "+message+" received");
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            queryResponseRcvd = msgArr[1];
            //Log.v(TAG,"queryResponseRcvd received "+queryResponseRcvd);
        }

        /**
         * Called when '*' is received
         * @param message
         */
        protected void handleGlobalQueryRcvdMessage(String message){
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String senderPort = msgArr[1];
            if(localPort.equals(senderPort)){
                globalResponse = message;
            }else{
                String newMessage = constructLocalDump(message);
                CommonUtil.unicastMessage(newMessage,successor.getPortNum());
            }
        }
        /**
         * Called when 'DELETE' is received
         * @param message
         */
        protected void handleDeleteMessage(String message){
            Log.v(TAG,"Inside handleDeleteMessage:: ");
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String senderPort = msgArr[1];
            String key = msgArr[2];
            if(localPort.equals(senderPort)) {
                Log.v(TAG,"Exiting as ring is completed ");
                return;
            }else if(CommonConstants.STRING_QUERY_GLOBAL_ALL.equals(key)){
                Log.v(TAG,"Deleting local files for *");
                deleteLocalFiles();
                CommonUtil.unicastMessage(message,successor.getPortNum());
                Log.v(TAG,"Forwarding delete all message: "+message+" to successor");
            }else{
                if(lookup(key)){
                   Log.v(TAG,"Deleting file "+key);
                   deleteSingleLocalFile(key);
                }else{
                    Log.v(TAG,"Forwarding single file deletion message to "+successor.getPortNum());
                    CommonUtil.unicastMessage(message, successor.getPortNum());
                }
            }
        }


    }



    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * @author stevko / pnandi
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        int seq = 0;
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket clientSocket = null;


            try {
                /**
                 * Infinite loop to receive messages from the client side
                 * continuously
                 */
                while(true) {
                    /**
                     * Open a secondary socket for the client and read data
                     * Reference - http://docs.oracle.com/javase/7/docs/api/java/io/BufferedReader.html
                     */
                    clientSocket = serverSocket.accept();
                    BufferedReader in =
                            new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String message = in.readLine();
                    Log.v(TAG,"Received mesage "+message);
                    new HandleMessageTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,message);

                    //saveMsg(seq,message);
                    //seq++;
                    /**
                     * publish progress to the UI
                     */
                    //publishProgress(message);
                    /**
                     * close the client socket
                     */
                    clientSocket.close();
                }
            }catch(IOException e){
                Log.e(TAG,"Cannot receive data");
            }
            return null;
        }



        /**
         * For debugging purposes
         * @param strings
         */
        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();

            saveMsg(seq,strReceived);
            Log.v(TAG, "Seq num "+seq);
            seq++;

            return;
        }

        /**
         * Calls content provider to save the received message
         * @param sequence
         * @param message
         */
        protected void saveMsg(int sequence,String message){


            ContentValues keyValueToInsert = new ContentValues();

            // inserting <”key-to-insert”, “value-to-insert”>

            String strSequence = String.valueOf(sequence);
            keyValueToInsert.put(CommonConstants.KEY_FIELD,strSequence);
            keyValueToInsert.put(CommonConstants.VALUE_FIELD, message);


            /*Uri newUri = getContentResolver().insert(
                    providerUri,    // assume we already created a Uri object with our provider URI
                    keyValueToInsert
            );*/

            insert(providerUri,keyValueToInsert);


        }
    }



}
