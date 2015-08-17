package edu.buffalo.cse.cse486586.simpledht;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.text.format.DateUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SimpleDhtActivity extends Activity {

    Button lDump = null;
    Button gDump = null;
    Uri providerUri;
    Button singleDelete = null;
    Button localDelete = null;
    Button globalDelete = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dht_main);
        providerUri = Uri.parse("content://" + "edu.buffalo.cse.cse486586.simpledht.provider"
                + "/edu.buffalo.cse.cse486586.simpledht.SimpleDhtProvider");
        
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));
        lDump = (Button)findViewById(R.id.button1);
        gDump = (Button)findViewById(R.id.button2);
/*        singleDelete = (Button)findViewById(R.id.button4);
        localDelete = (Button)findViewById(R.id.button5);
        globalDelete = (Button)findViewById(R.id.button6);*/

        lDump.setOnClickListener(new Button.OnClickListener(){
            TextView tv = (TextView) findViewById(R.id.textView1);
            public void onClick(View view){
                Cursor resultCursor = getContentResolver().query(providerUri, null,
                        CommonConstants.QUERY_LOCAL_ALL, null, null);
                tv.setText("");
                int keyIndex = resultCursor.getColumnIndex(CommonConstants.KEY_FIELD);
                int valueIndex = resultCursor.getColumnIndex(CommonConstants.VALUE_FIELD);
                resultCursor.moveToFirst();
                while(resultCursor.isAfterLast()==false){
                    String returnKey = resultCursor.getString(keyIndex);
                    String returnValue = resultCursor.getString(valueIndex);
                    //tv.append(returnKey+"$"+returnValue);
                    tv.append(returnKey);
                    tv.append("\n");
                    resultCursor.moveToNext();
                }
                resultCursor.close();

            }
        });
        gDump.setOnClickListener(new Button.OnClickListener(){
            TextView tv = (TextView) findViewById(R.id.textView1);
            public void onClick(View view){
                Cursor resultCursor = getContentResolver().query(providerUri, null,
                        CommonConstants.STRING_QUERY_GLOBAL_ALL, null, null);
                tv.setText("");
                int keyIndex = resultCursor.getColumnIndex(CommonConstants.KEY_FIELD);
                int valueIndex = resultCursor.getColumnIndex(CommonConstants.VALUE_FIELD);
                resultCursor.moveToFirst();
                while(resultCursor.isAfterLast()==false){
                    String returnKey = resultCursor.getString(keyIndex);
                    String returnValue = resultCursor.getString(valueIndex);
                    //tv.append(returnKey+"$"+returnValue);
                    tv.append(returnKey);
                    tv.append("\n");
                    resultCursor.moveToNext();
                }
                resultCursor.close();

            }
        });

        /*singleDelete.setOnClickListener(new Button.OnClickListener(){
            TextView tv = (TextView) findViewById(R.id.textView1);
            public void onClick(View view){
                getContentResolver().delete(providerUri, "5558",null);
            }
        });

        localDelete.setOnClickListener(new Button.OnClickListener(){
            TextView tv = (TextView) findViewById(R.id.textView1);
            public void onClick(View view){
                getContentResolver().delete(providerUri, CommonConstants.QUERY_LOCAL_ALL,null);
            }
        });

        globalDelete.setOnClickListener(new Button.OnClickListener(){
            TextView tv = (TextView) findViewById(R.id.textView1);
            public void onClick(View view){
                getContentResolver().delete(providerUri,CommonConstants.STRING_QUERY_GLOBAL_ALL,null);
            }
        });*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_simple_dht_main, menu);
        return true;
    }

}
