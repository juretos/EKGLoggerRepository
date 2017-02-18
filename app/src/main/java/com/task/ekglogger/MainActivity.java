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
import android.preference.PreferenceActivity;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    TextView rhText;
    EditText rrET;
    DataBaseHelper databaseHelper;
    private String IDENTIFICATION = "identification";
    private String RR = "rr";
    private String URL = "URL";


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
            HttpURLConnection httpcon;
            String result = null;
            int status = -1;
            String error;
            try {
                //Connect
                httpcon = (HttpURLConnection) ((new URL(url).openConnection()));
                httpcon.setDoOutput(true);
                httpcon.setRequestProperty("Content-Type", "application/json");
                httpcon.setRequestProperty("Accept", "application/json");
                httpcon.setRequestMethod("POST");
                httpcon.setConnectTimeout(socketTimeout);
                httpcon.setReadTimeout(socketTimeout);
                httpcon.connect();

                //Write
                if (data != null) {
                    OutputStream os = httpcon.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(data);
                    writer.close();
                    os.close();
                }

                //Read
                statusCode = httpcon.getResponseCode();
                Log.i(TAG, "Post status: " + statusCode);
                BufferedReader br = new BufferedReader(new InputStreamReader(httpcon.getInputStream()));
                String line = null;
                StringBuilder sb = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                result = sb.toString();
                if (result != null && statusCode == HttpsURLConnection.HTTP_OK) {
                    try {
                        JSONObject jsonObject = new JSONObject(result);
                        if (jsonObject.has("status")) {
                            status = jsonObject.getInt("status");
                        }
                        if (jsonObject.has("error")) {
                            error = jsonObject.getString("error");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
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
