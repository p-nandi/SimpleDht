package edu.buffalo.cse.cse486586.simpledht;

import android.util.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

/**
 * Created by phantom on 3/24/15.
 */
public class CommonUtil {

    static final String TAG = CommonUtil.class.getSimpleName();


    public static void unicastMessage(String message, String port) {
        Socket socket = null;
        try {
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(port));
            /**
             * Write out the msg into the socket
             */
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            out.write(message);
            out.flush();
            out.close();
            socket.close();
            Log.v(TAG,"unicastMessage:: "+message+"Message sent to "+port);
        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException");
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException");
        } catch (Exception e) {
            Log.e(TAG, "Some other exception occured in unicast");
            e.printStackTrace();;
        }finally {

        }
    }

    public static void sendPredSuccessorMsg(List<Node> chordNodes) {
        Collections.sort(chordNodes,new NodeHashValComparator());
        int N = chordNodes.size();
        Socket socket = null;
        Node predecessor,successor;
        //Log.v(TAG,"Sending message to "+chordNodes.size()+" nodes ");
        for (Node chordNode : chordNodes) {
                //Log.v(TAG,"Port "+chordNode.getPortNum()+" "+chordNode.getHashVal());
                String port = chordNode.getPortNum();
                int index = 0;
                for(int i=0;i<chordNodes.size();i++){
                    Node node = chordNodes.get(i);
                    if(node.getPortNum().equals(port)){
                        index = i;
                        break;
                    }
                }
                int predecessorIndex= (index-1+N)%N;
                predecessor = chordNodes.get(predecessorIndex);
                int successorIndex= (index+1)%N;
                successor = chordNodes.get(successorIndex);
                StringBuilder sb = new StringBuilder();
                sb.append(CommonConstants.MSG_TYPE_PRED_SUCC).append(CommonConstants.HASH_SEP)
                        .append(predecessor.getPortNum()).append(CommonConstants.HASH_SEP).append(successor.getPortNum());

                String message = sb.toString();
                unicastMessage(message,port);
        }
    }


    public static Boolean isLessThanEqual(String hashVal1,String hashVal2){
        int compareVal = hashVal1.compareTo(hashVal2);
        if(compareVal<=0){
            return true;
        }
        else{
            return false;
        }
        //return node1.getHashVal().compareTo(node1.getHashVal());
    }



}
