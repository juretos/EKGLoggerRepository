package com.task.ekglogger;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.task.ekglogger.database.DataBaseHelper;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class MainActivity extends AppCompatActivity {
    TextView rhText;
    EditText rrET;
    DataBaseHelper databaseHelper;
    private String IDENTIFICATION = "Identification";
    private String RR = "RR";
    private String URL = "URL";
    private String STATUS ="Status";
    private String ERROR ="Error";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rrET = (EditText) findViewById(R.id.rrInText);
        rhText = (TextView) findViewById(R.id.hrText);
        databaseHelper = new DataBaseHelper(this);


        rrET.setOnKeyListener(new View.OnKeyListener(){
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if(keyCode == event.KEYCODE_ENTER){
                    checkRRValue();
                    return true;
                }
                return false;
            }
        });

        updateAverageHeartRate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
                break;
        }

        return true;
    }

    public void save(View v){
        checkRRValue();
    }

    public void checkRRValue(){
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(rrET.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        if(!isNetworkAvailable()){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.no_network))
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //do things
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
        else if(rrET.getText().toString().isEmpty()){
            Toast.makeText(this, getResources().getString(R.string.enter_value), Toast.LENGTH_SHORT).show();
        }
        else{
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String mUrl = sharedPref.getString(URL, getResources().getString(R.string.default_url));
            try{
                int rr = Integer.parseInt(rrET.getText().toString());
                JSONObject jObject = new JSONObject();
                jObject.put(IDENTIFICATION, "3625086225");
                jObject.put(RR, rr);
                new PostAsyncTask(this,rr).execute(mUrl,jObject.toString());
            }catch (JSONException e) {
                e.printStackTrace();
            }catch(NumberFormatException  e){
                e.printStackTrace();
                Toast.makeText(this, getResources().getString(R.string.integer), Toast.LENGTH_SHORT).show();
            }
        }

    }

    public void saveAndUpdate(int rr){
        try{
            databaseHelper.insertRRdata(System.currentTimeMillis(),rr);
            updateAverageHeartRate();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void updateAverageHeartRate(){
        double averageRR = databaseHelper.getAverageRRData(3);
        if(averageRR==-1){
            rhText.setText(getResources().getString(R.string.no_data));
        }
        else{
            int one_minute = 60*1000;
            int bpm = (int) Math.round(one_minute/averageRR);
            rhText.setText(String.format("%d "+getResources().getString(R.string.bpm),bpm));
        }
    }



    class PostAsyncTask extends AsyncTask<String, Integer, Integer> {
        private int socketTimeout = 10 * 1000;
        private String TAG = this.getClass().getName();
        Context context;
        ProgressDialog pd;
        int rr;

        public PostAsyncTask(Context context, int rr) {
            this.context = context;
            this.rr = rr;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd = new ProgressDialog(MainActivity.this);
            pd.setMessage(getResources().getString(R.string.check_rr_value));
            pd.show();
        }

        @Override
        protected Integer doInBackground(String... params) {
            if (params.length < 2)
                return -1;
            String url = params[0];
            String data = params[1];
            int statusCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
            String result;
            int status = -1;
            String error;

            HttpParams httpParameters = new BasicHttpParams();
            httpParameters.setParameter(CoreProtocolPNames.USER_AGENT,
                    System.getProperty("http.agent"));
            // Set the timeout in milliseconds until a connection is established.
            // The default value is zero, that means the timeout is not used.
            int timeoutConnection = 60*1000;
            HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
            // Set the default socket timeout (SO_TIMEOUT)
            // in milliseconds which is the timeout for waiting for data.
            int timeoutSocket = 60*1000;

            HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
            HttpClient httpclient = new DefaultHttpClient(httpParameters);
            HttpPost httppost = new HttpPost(url);
            httppost.setHeader("Content-Type", "application/json");
            try {
                StringEntity postingString = new StringEntity(data);
                httppost.setEntity(postingString);
                HttpResponse httpResponse = null;
                httpResponse = httpclient.execute(httppost);
                statusCode = httpResponse.getStatusLine().getStatusCode();
                HttpEntity entity = httpResponse.getEntity();
                if (entity != null) {
                    InputStream input = null;
                    input = entity.getContent();
                    BufferedReader br = new BufferedReader(new InputStreamReader(input));
                    String line = null;
                    StringBuilder sb = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    result = sb.toString();
                    Log.i(TAG, result);
                    if (result != null) {
                        try {
                            JSONObject jsonObject = new JSONObject(result);
                            if (jsonObject.has(STATUS)) {
                                status = jsonObject.getInt(STATUS);
                            }
                            if (jsonObject.has(ERROR)) {
                                error = jsonObject.getString(ERROR);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
            return status;
        }

        protected void onPostExecute(Integer status) {
            super.onPostExecute(status);
            if (pd != null) {
                pd.dismiss();
            }
            if (status == 1) {
                saveAndUpdate(rr);
            }
            else if(status==0){
                Toast.makeText(MainActivity.this, getResources().getString(R.string.rr_invalid), Toast.LENGTH_SHORT).show();
            }
            else{//-1
                Toast.makeText(MainActivity.this, getResources().getString(R.string.internal_server_error), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
