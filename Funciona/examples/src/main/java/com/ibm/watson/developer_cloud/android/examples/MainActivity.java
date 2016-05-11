 /**
  * © Copyright IBM Corporation 2015
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  **/

package com.ibm.watson.developer_cloud.android.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Vector;

import android.app.FragmentTransaction;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.app.ActionBar;
import android.app.Fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import com.rey.material.widget.Button;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.FrameLayout;
import com.rey.material.widget.Spinner;


// IBM Watson SDK
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.android.speech_common.v1.TokenProvider;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

 public class MainActivity extends Activity implements ISpeechDelegate {

	private static final String TAG = "MainActivity";

     private enum ConnectionState {
         IDLE, CONNECTING, CONNECTED
     }

     ConnectionState mState = ConnectionState.IDLE;
	TextView textTTS;
    public JSONObject jsonVoices = null;
     public JSONObject jsonModels = null;
    private Handler mHandler = null;
     private static String mRecognitionResults = "";

    //WATSON
    private final String mLogTag = "Inside WatsonQueryFragment: ";
    private String mWatsonQueryString = "";
    private String mWatsonAnswerString = "";
    private boolean mIsQuerying = false;
    private WatsonQuery mQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (Build.VERSION.SDK_INT < 16) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tab_tts);

        //WATSON
        if(mWatsonAnswerString.length() > 0) {
            TextView watsonQuestion = (TextView)findViewById(R.id.watson_answer_text);
            watsonQuestion.setText(mWatsonAnswerString);
        }

        // event binding for submit button
        findViewById(R.id.watson_submit_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mIsQuerying) {
                    mIsQuerying = true;
                    EditText watsonQuestion = (EditText)findViewById(R.id.watson_question_text);
                    if(watsonQuestion.getText() != null) {
                        mWatsonQueryString = watsonQuestion.getText().toString();
                    }
                    System.out.println("WatsonPregunta: "+ watsonQuestion.getText().toString());
                    mQuery = new WatsonQuery();
                    mQuery.execute();
                }
                hideSoftKeyboard(MainActivity.this);
            }
        });

        // hide keyboard when clicking off text edit element
        /*findViewById(R.id.rootLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSoftKeyboard(getActivity());
            }
        });*/

        //getActivity().findViewById(R.id.rootLayout).requestFocus();

        //TEXT TO SPEECH
        setText();
        if (initTTS() == false) {
            TextView viewPrompt = (TextView) findViewById(R.id.watson_answer_text);
            viewPrompt.setText("Error: no authentication credentials or token available, please enter your authentication information");
        }

        if (jsonVoices == null) {
            jsonVoices = new TTSCommands().doInBackground();
            if (jsonVoices == null) {
            }
        }
        addItemsOnSpinnerVoices();
        updatePrompt(getString(R.string.voiceDefault));

        Spinner spinner = (Spinner) findViewById(R.id.spinnerVoices);


        mHandler = new Handler();

        //STT
        if (initSTT() == false) {
            TextView viewPrompt = (TextView) findViewById(R.id.watson_answer_text);
            viewPrompt.setText("Error: no authentication credentials or token available, please enter your authentication information");
        }

        if (jsonModels == null) {
            jsonModels = new STTCommands().doInBackground();
            if (jsonModels == null) {

            }
        }
        addItemsOnSpinnerModels();
        Button buttonRecord = (Button)findViewById(R.id.buttonRecord);
        buttonRecord.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                if (mState == ConnectionState.IDLE) {
                    mState = ConnectionState.CONNECTING;
                    Log.d(TAG, "onClickRecord: IDLE -> CONNECTING");
                    Spinner spinner = (Spinner)findViewById(R.id.spinnerModels);
                    spinner.setEnabled(false);
                    mRecognitionResults = "";
                    displayResult(mRecognitionResults);
                    ItemModel item = (ItemModel)spinner.getSelectedItem();
                    SpeechToText.sharedInstance().setModel(item.getModelName());
                    displayStatus("connecting to the STT service...");
                    // start recognition
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... none) {
                            SpeechToText.sharedInstance().recognize();
                            return null;
                        }
                    }.execute();
                    //setButtonLabel(R.id.buttonRecord, "Connecting...");
                    setButtonState(true);
                }
                else if (mState == ConnectionState.CONNECTED) {
                    mState = ConnectionState.IDLE;
                    Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");
                    Spinner spinner = (Spinner)findViewById(R.id.spinnerModels);
                    spinner.setEnabled(true);
                    SpeechToText.sharedInstance().stopRecognition();
                    setButtonState(false);
                }
            }
        });


    }

     //STT
     protected void addItemsOnSpinnerModels() {

         Spinner spinner = (Spinner)findViewById(R.id.spinnerModels);
         int iIndexDefault = 0;

         JSONObject obj = jsonModels;
         ItemModel [] items = null;
         try {
             JSONArray models = obj.getJSONArray("models");

             // count the number of Broadband models (narrowband models will be ignored since they are for telephony data)
             Vector<Integer> v = new Vector<>();
             for (int i = 0; i < models.length(); ++i) {
                 if (models.getJSONObject(i).getString("name").indexOf("Broadband") != -1) {
                     v.add(i);
                 }
             }
             items = new ItemModel[v.size()];
             int iItems = 0;
             for (int i = 0; i < v.size() ; ++i) {
                 items[iItems] = new ItemModel(models.getJSONObject(v.elementAt(i)));
                 if (models.getJSONObject(v.elementAt(i)).getString("name").equals(getString(R.string.modelDefault))) {
                     iIndexDefault = iItems;
                 }
                 ++iItems;
             }
         } catch (JSONException e) {
             e.printStackTrace();
         }

         if (items != null) {
             ArrayAdapter<ItemModel> spinnerArrayAdapter = new ArrayAdapter<ItemModel>(this, android.R.layout.simple_spinner_item, items);
             spinner.setAdapter(spinnerArrayAdapter);
             spinner.setSelection(iIndexDefault);
         }
     }

     private String getModelSelected() {

         Spinner spinner = (Spinner)findViewById(R.id.spinnerModels);
         ItemModel item = (ItemModel)spinner.getSelectedItem();
         return item.getModelName();
     }

     public class ItemModel {

         private JSONObject mObject = null;

         public ItemModel(JSONObject object) {
             mObject = object;
         }

         public String toString() {
             try {
                 return mObject.getString("description");
             } catch (JSONException e) {
                 e.printStackTrace();
                 return null;
             }
         }

         public String getModelName() {
             try {
                 return mObject.getString("name");
             } catch (JSONException e) {
                 e.printStackTrace();
                 return null;
             }
         }
     }
     public void displayResult(final String result) {
         final Runnable runnableUi = new Runnable(){
             @Override
             public void run() {
                 TextView textResult = (TextView)findViewById(R.id.watson_question_text);
                 textResult.setText(result);
             }
         };

         new Thread(){
             public void run(){
                 mHandler.post(runnableUi);
             }
         }.start();
     }

     public void displayStatus(final String status) {
            /*final Runnable runnableUi = new Runnable(){
                @Override
                public void run() {
                    TextView textResult = (TextView)mView.findViewById(R.id.sttStatus);
                    textResult.setText(status);
                }
            };
            new Thread(){
                public void run(){
                    mHandler.post(runnableUi);
                }
            }.start();*/
     }



     /**
      * Change the button's label
      */
     public void setButtonLabel(final int buttonId, final String label) {
         final Runnable runnableUi = new Runnable(){
             @Override
             public void run() {
                 Button button = (Button)findViewById(buttonId);
                 button.setText(label);
             }
         };
         new Thread(){
             public void run(){
                 mHandler.post(runnableUi);
             }
         }.start();
     }

     /**
      * Change the button's drawable
      */
     public void setButtonState(final boolean bRecording) {

         final Runnable runnableUi = new Runnable(){
             @Override
             public void run() {
                 int iDrawable = bRecording ? R.drawable.button_record_stop : R.drawable.button_record_start;
                 Button btnRecord = (Button)findViewById(R.id.buttonRecord);
                 btnRecord.setBackground(getResources().getDrawable(iDrawable));
             }
         };
         new Thread(){
             public void run(){
                 mHandler.post(runnableUi);
             }
         }.start();
     }

     // delegages ----------------------------------------------

     public void onOpen() {
         Log.d(TAG, "onOpen");
         displayStatus("successfully connected to the STT service");
         setButtonLabel(R.id.buttonRecord, "Stop recording");
         mState = ConnectionState.CONNECTED;
     }

     public void onError(String error) {

         Log.e(TAG,"hola"+ error);
        // displayResult(error);
         mState = ConnectionState.IDLE;
     }

     public void onClose(int code, String reason, boolean remote) {
         Log.d(TAG, "onClose, code: " + code + " reason: " + reason);
         displayStatus("connection closed");
         setButtonLabel(R.id.buttonRecord, "Record");
         mState = ConnectionState.IDLE;
     }

     public void onMessage(String message) {

         Log.d(TAG, "onMessage, message: " + message);
         try {
             JSONObject jObj = new JSONObject(message);
             // state message
             if(jObj.has("state")) {
                 Log.d(TAG, "Status message: " + jObj.getString("state"));
             }
             // results message
             else if (jObj.has("results")) {
                 //if has result
                 Log.d(TAG, "Results message: ");
                 JSONArray jArr = jObj.getJSONArray("results");
                 for (int i=0; i < jArr.length(); i++) {
                     JSONObject obj = jArr.getJSONObject(i);
                     JSONArray jArr1 = obj.getJSONArray("alternatives");
                     String str = jArr1.getJSONObject(0).getString("transcript");
                     // remove whitespaces if the language requires it
                     String model = this.getModelSelected();
                     if (model.startsWith("ja-JP") || model.startsWith("zh-CN")) {
                         str = str.replaceAll("\\s+","");
                     }
                     String strFormatted = Character.toUpperCase(str.charAt(0)) + str.substring(1);
                     if (obj.getString("final").equals("true")) {
                         String stopMarker = (model.startsWith("ja-JP") || model.startsWith("zh-CN")) ? "。" : ". ";
                         mRecognitionResults += strFormatted.substring(0,strFormatted.length()-1) + stopMarker;
                         displayResult(mRecognitionResults);
                     } else {
                         displayResult(mRecognitionResults + strFormatted);
                     }
                     break;
                 }
             } else {
                 displayResult("unexpected data coming from stt server: \n" + message);
             }

         } catch (JSONException e) {
             Log.e(TAG, "Error parsing JSON");
             e.printStackTrace();
         }
     }

     public void onAmplitude(double amplitude, double volume) {
         //Logger.e(TAG, "amplitude=" + amplitude + ", volume=" + volume);
     }

     //TTS

    public URI getHost(String url){
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean initTTS() {

        // DISCLAIMER: please enter your credentials or token factory in the lines below

        String username = getString(R.string.TTSdefaultUsername);
        String password = getString(R.string.TTSdefaultPassword);
        String tokenFactoryURL = getString(R.string.TTSdefaultTokenFactory);
        String serviceURL = "https://stream.watsonplatform.net/text-to-speech/api";

        TextToSpeech.sharedInstance().initWithContext(this.getHost(serviceURL));

        // token factory is the preferred authentication method (service credentials are not distributed in the client app)
           /* if (tokenFactoryURL.equals(getString(R.string.defaultTokenFactory)) == true) {
                TextToSpeech.sharedInstance().setTokenProvider(new MyTokenProvider(tokenFactoryURL));
            }
            // Basic Authentication
            else if (username.equals(getString(R.string.defaultUsername)) == true) {*/
        TextToSpeech.sharedInstance().setCredentials(username, password);
            /*} else {
                // no authentication method available
                return false;
            }*/

        TextToSpeech.sharedInstance().setVoice(getString(R.string.voiceDefault));

        return true;
    }

    protected void setText() {

        Typeface roboto = Typeface.createFromAsset(getApplicationContext().getAssets(), "font/Roboto-Bold.ttf");
        Typeface notosans = Typeface.createFromAsset(getApplicationContext().getAssets(), "font/NotoSans-Regular.ttf");

        //TextView viewTitle = (TextView)findViewById(R.id.title);
        String strTitle = getString(R.string.ttsTitle);
        SpannableString spannable = new SpannableString(strTitle);
        spannable.setSpan(new AbsoluteSizeSpan(47), 0, strTitle.length(), 0);
        spannable.setSpan(new CustomTypefaceSpan("", roboto), 0, strTitle.length(), 0);
        //viewTitle.setText(spannable);
        //viewTitle.setTextColor(0xFF325C80);

        //TextView viewInstructions = (TextView)findViewById(R.id.instructions);
        String strInstructions = getString(R.string.ttsInstructions);
        SpannableString spannable2 = new SpannableString(strInstructions);
        spannable2.setSpan(new AbsoluteSizeSpan(20), 0, strInstructions.length(), 0);
        spannable2.setSpan(new CustomTypefaceSpan("", notosans), 0, strInstructions.length(), 0);
        //viewInstructions.setText(spannable2);
        //viewInstructions.setTextColor(0xFF121212);
    }

    public class ItemVoice {

        public JSONObject mObject = null;

        public ItemVoice(JSONObject object) {
            mObject = object;
        }

        public String toString() {
            try {
                return mObject.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public void addItemsOnSpinnerVoices() {

        Spinner spinner = (Spinner)findViewById(R.id.spinnerVoices);
        int iIndexDefault = 0;

        JSONObject obj = jsonVoices;
        ItemVoice [] items = null;
        try {
            JSONArray voices = obj.getJSONArray("voices");
            items = new ItemVoice[voices.length()];
            for (int i = 0; i < voices.length(); ++i) {
                items[i] = new ItemVoice(voices.getJSONObject(i));
                if (voices.getJSONObject(i).getString("name").equals(getString(R.string.voiceDefault))) {
                    iIndexDefault = i;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (items != null) {
            ArrayAdapter<ItemVoice> spinnerArrayAdapter = new ArrayAdapter<ItemVoice>(this, android.R.layout.simple_spinner_item, items);
            spinner.setAdapter(spinnerArrayAdapter);
            spinner.setSelection(iIndexDefault);
        }
    }

    // return the selected voice
    public String getSelectedVoice() {

        // return the selected voice
        Spinner spinner = (Spinner)findViewById(R.id.spinnerVoices);
        ItemVoice item = (ItemVoice)spinner.getSelectedItem();
        String strVoice = null;
        try {
            strVoice = item.mObject.getString("name");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return strVoice;
    }

    // update the prompt for the selected voice
    public void updatePrompt(final String strVoice) {

        TextView viewPrompt = (TextView)findViewById(R.id.watson_answer_text);
        if (strVoice.startsWith("en-US") || strVoice.startsWith("en-GB")) {
            viewPrompt.setText(getString(R.string.ttsEnglishPrompt));
        } else if (strVoice.startsWith("es-ES")) {
            viewPrompt.setText(getString(R.string.ttsSpanishPrompt));
        } else if (strVoice.startsWith("fr-FR")) {
            viewPrompt.setText(getString(R.string.ttsFrenchPrompt));
        } else if (strVoice.startsWith("it-IT")) {
            viewPrompt.setText(getString(R.string.ttsItalianPrompt));
        } else if (strVoice.startsWith("de-DE")) {
            viewPrompt.setText(getString(R.string.ttsGermanPrompt));
        } else if (strVoice.startsWith("ja-JP")) {
            viewPrompt.setText(getString(R.string.ttsJapanesePrompt));
        }
    }

    public static class TTSCommands extends AsyncTask<Void, Void, JSONObject> {

        protected JSONObject doInBackground(Void... none) {

            return TextToSpeech.sharedInstance().getVoices();
        }
    }

     public static class STTCommands extends AsyncTask<Void, Void, JSONObject> {

         protected JSONObject doInBackground(Void... none) {

             return SpeechToText.sharedInstance().getModels();
         }
     }


     static class MyTokenProvider implements TokenProvider {

        String m_strTokenFactoryURL = null;

        public MyTokenProvider(String strTokenFactoryURL) {
            m_strTokenFactoryURL = strTokenFactoryURL;
        }

        public String getToken() {

            Log.d(TAG, "attempting to get a token from: " + m_strTokenFactoryURL);
            try {
                // DISCLAIMER: the application developer should implement an authentication mechanism from the mobile app to the
                // server side app so the token factory in the server only provides tokens to authenticated clients
                HttpClient httpClient = new DefaultHttpClient();
                HttpGet httpGet = new HttpGet(m_strTokenFactoryURL);
                HttpResponse executed = httpClient.execute(httpGet);
                InputStream is = executed.getEntity().getContent();
                StringWriter writer = new StringWriter();
                IOUtils.copy(is, writer, "UTF-8");
                String strToken = writer.toString();
                Log.d(TAG, strToken);
                return strToken;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * Play TTS Audio data
	 * 
	 * @param view
	 */
	public void playTTS(View view) throws JSONException {

        TextToSpeech.sharedInstance().setVoice(getSelectedVoice());
        Log.d(TAG, getSelectedVoice());

		//Get text from text box
		textTTS = (TextView)findViewById(R.id.watson_answer_text);
		String ttsText=textTTS.getText().toString();
		Log.d(TAG, ttsText);
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(textTTS.getWindowToken(),
				InputMethodManager.HIDE_NOT_ALWAYS);

		//Call the sdk function
		TextToSpeech.sharedInstance().synthesize(ttsText);
	}


    //****************WATSON*******************************
    private class WatsonQuery extends AsyncTask<Void, Integer, String> {

        private SSLContext context;
        private HttpsURLConnection connection;
        private String jsonData;

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected String doInBackground(Void... ignore) {

            // establish SSL trust (insecure for demo)
            try {
                context = SSLContext.getInstance("TLS");
                context.init(null, trustAllCerts, new java.security.SecureRandom());
            } catch (java.security.KeyManagementException e) {
                e.printStackTrace();
            } catch (java.security.NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            try {
                // Default HTTPS connection option values
                URL watsonURL = new URL(getString(R.string.user_watson_server_instance));
                int timeoutConnection = 30000;
                connection = (HttpsURLConnection) watsonURL.openConnection();
                connection.setSSLSocketFactory(context.getSocketFactory());
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setConnectTimeout(timeoutConnection);
                connection.setReadTimeout(timeoutConnection);

                // Watson specific HTTP headers
                connection.setRequestProperty("X-SyncTimeout", "30");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Basic " + getEncodedValues(getString(R.string.user_id), getString(R.string.user_password)));
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Cache-Control", "no-cache");

                OutputStream out = connection.getOutputStream();
                String query = "{\"question\": {\"questionText\": \"" + mWatsonQueryString + "\"}}";
                out.write(query.getBytes());
                out.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

            int responseCode;
            try {
                if (connection != null) {
                    responseCode = connection.getResponseCode();
                   // Log.i(mLogTag, "Server Response Code: " + Integer.toString(responseCode));

                    switch(responseCode) {
                        case 200:
                            // successful HTTP response state
                            InputStream input = connection.getInputStream();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                            String line;
                            StringBuilder response = new StringBuilder();
                            while((line = reader.readLine()) != null) {
                                response.append(line);
                                response.append('\r');
                            }
                            reader.close();

                            //Log.i(mLogTag, "Watson Output: " + response.toString());
                            jsonData = response.toString();

                            break;
                        default:
                            // Do Stuff
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // received data, deliver JSON to PostExecute
            if(jsonData != null) {
                return jsonData;
            }

            // else, hit HTTP error, handle in PostExecute by doing null check
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... percent) {
            if (this != null) {
                this.onProgressUpdate(percent[0]);
            }
        }

        @Override
        protected void onCancelled() {
            if (this != null) {
                this.onCancelled();
            }
        }

        @Override
        protected void onPostExecute(String json) {
            mIsQuerying = false;
            if (this != null) {
                //this.onPostExecute();
            }

            try {
                if(json != null) {
                    JSONObject watsonResponse = new JSONObject(json);
                    JSONObject question = watsonResponse.getJSONObject("question");
                    JSONArray evidenceArray = question.getJSONArray("evidencelist");
                    JSONObject mostLikelyValue = evidenceArray.getJSONObject(0);
                    mWatsonAnswerString = mostLikelyValue.get("text").toString();
                    TextView textView = (TextView) findViewById(R.id.watson_answer_text);
                    textView.setText(mWatsonAnswerString);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                // No valid answern
                printTryDifferentQuestion();
            }
        }

        /*
         *  Accepts all HTTPS certs. Do NOT use in production!!!
         */
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[] {};
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
        }};
    }



    private void printTryDifferentQuestion() {
        TextView textView = (TextView) findViewById(R.id.watson_answer_text);
        textView.setText("Please try a different question.");
    }

    private String getEncodedValues(String user_id, String user_password) {
        String textToEncode = user_id + ":" + user_password;
        byte[] data = null;
        try {
            data = textToEncode.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String base64 = Base64.encodeToString(data, Base64.DEFAULT);
        return base64;
    }

    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager)  activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if(activity.getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
        }
    }

     //STT

     private boolean initSTT() {

         // DISCLAIMER: please enter your credentials or token factory in the lines below
         String username = getString(R.string.defaultUsername);
         String password = getString(R.string.defaultPassword);

         String tokenFactoryURL = getString(R.string.defaultTokenFactory);
         String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";

         SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS);
         //SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_DEFAULT);

         SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), this.getApplicationContext(), sConfig);

         // token factory is the preferred authentication method (service credentials are not distributed in the client app)
           /* if (tokenFactoryURL.equals(getString(R.string.defaultTokenFactory)) == true) {
                SpeechToText.sharedInstance().setTokenProvider(new MyTokenProvider(tokenFactoryURL));
            }
            // Basic Authentication
            else if (username.equals(getString(R.string.defaultUsername)) == true) { */
         SpeechToText.sharedInstance().setCredentials(username, password);
           /* } else {
                // no authentication method available
                return false;
            }
*/
         SpeechToText.sharedInstance().setModel(getString(R.string.modelDefault));
         SpeechToText.sharedInstance().setDelegate(this);

         return true;
     }
}
