package com.raghavgujjar.imageupload;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.os.Build;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
        public static final String IMAGE_DIRECTORY_NAME = "Image Upload";
        private String mCurrentPhotoPath;
        private ImageView mImageView;
        private Button buttonTake;
        private Button buttonUpload;
        private Button buttonCancel;
        private TextView titleText;
        private TextView descriptionText;
        private Bitmap bitmap;

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            mImageView = (ImageView) rootView.findViewById(R.id.imageView);
            buttonTake = (Button)rootView.findViewById(R.id.button_take);
            buttonUpload = (Button)rootView.findViewById(R.id.button_upload);
            buttonCancel = (Button)rootView.findViewById(R.id.button_cancel);
            titleText = (TextView) rootView.findViewById(R.id.title_text);
            descriptionText = (TextView) rootView.findViewById(R.id.description_text);

            if (savedInstanceState !=null && savedInstanceState.getString("path") !=null) {
                mCurrentPhotoPath = savedInstanceState.getString("path");
                buttonTake.setVisibility(View.GONE);
                buttonUpload.setVisibility(View.VISIBLE);
                buttonCancel.setVisibility(View.VISIBLE);
                setPic();
            }

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
                        cancelForm();
                        new UploadImageTask(getActivity()).execute(bitmap);
                    }
                    else {
                        Toast.makeText(getActivity(), "No Internet Connection", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            buttonCancel.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v) {
                    cancelForm();
                    mCurrentPhotoPath = null;
                    bitmap = null;
                }
            });
            return rootView;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString("path", mCurrentPhotoPath);
        }

        private void cancelForm() {
            buttonTake.setVisibility(View.VISIBLE);
            buttonUpload.setVisibility(View.GONE);
            buttonCancel.setVisibility(View.GONE);
            mImageView.setImageDrawable(null);
            titleText.setText("");
            descriptionText.setText("");
        }

        private File createImageFile() throws IOException {
            File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES),IMAGE_DIRECTORY_NAME);

            if (!storageDir.exists()) {
                if (!storageDir.mkdirs()) {
                    Log.d("FAILED_IMAGE_DIRECTORY", "Failed to create "
                            + IMAGE_DIRECTORY_NAME + " directory");
                    return null;
                }
            }
            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File image = new File(storageDir.getPath()+ File.separator + "IMG_" + timeStamp + ".png");
            return image;
        }

        private void dispatchTakePictureIntent() {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                    mCurrentPhotoPath = photoFile.getAbsolutePath();
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));

                } catch (IOException ioe) {
                    photoFile = null;
                    mCurrentPhotoPath = null;
                    Log.e("IMAGE_FILE_FAILED", ioe.getMessage());
                }
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
            else {
                Toast.makeText(getActivity(), "No camera available", Toast.LENGTH_SHORT).show();
            }
        }

        private void setPic() {
            // Get the dimensions of the View
            int targetW = mImageView.getWidth();
            int targetH = mImageView.getHeight();

            // Get the dimensions of the bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;

            // Determine how much to scale down the image
            int scaleFactor = 1;
            if ((targetW > 0) || (targetH > 0)) {
                scaleFactor = Math.min(photoW/targetW, photoH/targetH);
            }

            // Decode the image file into a Bitmap sized to fill the View
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;
            bmOptions.inPurgeable = true;

            try {
                bitmap = reOrient(mCurrentPhotoPath, bmOptions);
                mImageView.setImageBitmap(bitmap);
            } catch(IOException ioe) {
                Log.d("ERROR IN ORIENTING",ioe.getMessage());
            }
        }

        private Bitmap reOrient(String path, BitmapFactory.Options options) throws IOException {
            ExifInterface exif = new ExifInterface(path);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }

            Matrix matrix = new Matrix();
            matrix.preRotate(rotate);
            Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, options);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), matrix, false);
            return bitmap;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
                buttonTake.setVisibility(View.GONE);
                buttonUpload.setVisibility(View.VISIBLE);
                buttonCancel.setVisibility(View.VISIBLE);
                setPic();
            }
        }

        public class UploadImageTask extends AsyncTask<Bitmap, Void, String> {
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

            protected void onPreExecute() {
                createNotification("Progress","Photo upload in progress");
            }

            @Override
            protected String doInBackground(Bitmap... imageBitmap) {
                InputStream is = null;
                HttpURLConnection conn = null;
                byte[] pictureBytes;
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    imageBitmap[0].compress(Bitmap.CompressFormat.PNG, 95, bos);
                    byte[] ba = bos.toByteArray();
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
                bitmap = null;
                mCurrentPhotoPath = null;
                if (error) {
                    createNotification(content, "Upload Failed");
                }
                else {
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
                        .setSmallIcon(android.R.drawable.stat_sys_upload)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true);

                if (title.equals("Success")) {
                    Uri webPage = Uri.parse("http://imageupload-env.elasticbeanstalk.com/uploadedImage/uploadedImage.png");
                    Intent resultIntent = new Intent(Intent.ACTION_VIEW, webPage);
                    PendingIntent resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setAutoCancel(true)
                            .setTicker("The photo was uploaded successfully")
                            .setContentIntent(resultPendingIntent);
                }
                if (title.equals("Failed") || title.equals("Error")) {
                    builder.setSmallIcon(android.R.drawable.stat_notify_error)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setAutoCancel(true)
                            .setTicker("The photo failed to upload");
                }

                notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            }
        }
    }
}
