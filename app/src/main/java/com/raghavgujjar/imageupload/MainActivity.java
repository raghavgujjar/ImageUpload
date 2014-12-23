package com.raghavgujjar.imageupload;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        private static final int REQUEST_IMAGE_CAPTURE = 1;
        private ImageView mImageView;
        private Button buttonTake;
        private Button buttonUpload;

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            mImageView = (ImageView) rootView.findViewById(R.id.imageView);
            buttonTake = (Button)rootView.findViewById(R.id.button_take);
            buttonUpload = (Button)rootView.findViewById(R.id.button_upload);
            buttonTake.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v) {
                    dispatchTakePictureIntent();
                }
            });
            buttonUpload.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v) {
                    ConnectivityManager connMgr = (ConnectivityManager)
                            getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.isConnected()) {
                        BitmapDrawable drawable = (BitmapDrawable) mImageView.getDrawable();
                        Bitmap imageBitmap = drawable.getBitmap();
                        Log.d("After reading","width="+imageBitmap.getWidth()+" height="+imageBitmap.getHeight());
                        new UploadImageTask(getActivity()).execute(imageBitmap);
                    }
                    else {
                        Toast.makeText(getActivity(), "No Internet Connection", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return rootView;
        }

        private void dispatchTakePictureIntent() {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
                Bundle extras = data.getExtras();
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                Log.d("After taking","width="+imageBitmap.getWidth()+" height="+imageBitmap.getHeight());
                buttonTake.setVisibility(View.GONE);
                buttonUpload.setVisibility(View.VISIBLE);
                mImageView.setImageBitmap(imageBitmap);
            }
        }

        private class UploadImageTask extends AsyncTask<Bitmap, Void, String> {
            private boolean error = false;
            private Context context;
            private int NOTIFICATION_ID = 1;
            private NotificationManager notificationManager;
            private String content = null;
            private int statusCode;

            public UploadImageTask(Context context) {
                this.context = context;
                notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            }

            @Override
            protected String doInBackground(Bitmap... imageBitmap) {
                InputStream is = null;
                HttpURLConnection conn = null;
                try {
                    //Construct Image Data
                    Bitmap bitmapImage = imageBitmap[0];
                    ByteArrayOutputStream bao = new ByteArrayOutputStream();
                    double width = bitmapImage.getWidth();
                    double height = bitmapImage.getHeight();
                    double ratio = 400/width;
                    int newHeight = (int)(ratio*height);
                    bitmapImage = Bitmap.createScaledBitmap(bitmapImage, 400, newHeight, true);
                    Log.d("New height and width: ","width="+bitmapImage.getWidth()+" height="+bitmapImage.getHeight());
                    bitmapImage.compress(Bitmap.CompressFormat.PNG, 95, bao);
                    byte[] ba = bao.toByteArray();
                    String imageData = Base64.encodeToString(ba, Base64.DEFAULT);

                    //Create URL and param
                    URL url = new URL("http://imageupload-env.elasticbeanstalk.com/UploadService/upload");
                    String params = "image=" + URLEncoder.encode(imageData, "UTF-8");

                    //Prepare connection
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(60000);
                    conn.setConnectTimeout(60000);
                    conn.setRequestMethod("POST");
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    //Pass param
                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(os, "UTF-8"));
                    writer.write(params);
                    writer.flush();
                    writer.close();

                    conn.connect();

                    // handle connection issues
                    statusCode = conn.getResponseCode();
                    if (statusCode != HttpURLConnection.HTTP_OK) {
                        throw new Exception("Bad Status");
                    }

                    //receive result
                    String response = "";
                    is = conn.getInputStream();
                    Scanner inStream = new Scanner(is);
                    while(inStream.hasNextLine()) {
                        response += (inStream.nextLine());
                    }

                    //process response
                    Log.d("Response is", response);
                    boolean status = handleResponse(response);
                    content = "Success";
                    if (!status) {
                        throw new Exception("Invalid Input");
                    }
                } catch(JSONException jse) {
                    Log.w("JSON_ERROR", jse);
                    error = true;
                    content = "Failed";
                    cancel(true);
                } catch(SocketTimeoutException ste) {
                    Log.w("SOCKET_TIMEOUT", ste);
                    Log.d("STATUS_CODE", Integer.toString(statusCode));
                    error = true;
                    content = "Failed";
                    cancel(true);
                } catch(IOException ioe) {
                    Log.w("IO_ERROR", ioe);
                    error = true;
                    content = "Failed";
                    cancel(true);
                } catch(Exception e) {
                    Log.w("INVALID_DATA", e);
                    error = true;
                    content = "Failed";
                    cancel(true);
                }
                finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                return content;
            }

            protected void onCancelled() {
                createNotification("Error", "Error while uploading");
            }

            protected void onPostExecute(String content) {
                if (error) {
                    createNotification(content, "Upload Failed");
                }
                else {
                    buttonTake.setVisibility(View.VISIBLE);
                    buttonUpload.setVisibility(View.GONE);
                    mImageView.setImageDrawable(null);
                    createNotification(content, "Image Uploaded");
                }
            }

            private boolean handleResponse(String response) throws JSONException {
                if (response != null) {
                    JSONObject responseObject = new JSONObject(response);
                    if (responseObject.getString("Status").equals("Uploaded")) {
                        return true;
                    }
                    else {
                        return false;
                    }
                } else {
                    return false;
                }
            }

            private void createNotification(String title, String text) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                        .setContentTitle(title)
                        .setContentText(text);

                notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            }
        }
    }
}
