package com.mr.flutter.plugin.filepicker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.util.ArrayList;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class FilePickerDelegate implements PluginRegistry.ActivityResultListener {

    private static final String TAG = "FilePickerDelegate";
    private static final int REQUEST_CODE = (FilePickerPlugin.class.hashCode() + 43) & 0x0000ffff;

    private final Activity activity;
    private MethodChannel.Result pendingResult;
    private boolean isMultipleSelection = false;
    private String type;

    public FilePickerDelegate(final Activity activity) {
        this(activity, null);
    }

    @VisibleForTesting
    FilePickerDelegate(final Activity activity, final MethodChannel.Result result) {
        this.activity = activity;
        this.pendingResult = result;
    }

    @Override
    public boolean onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (data != null) {
                        if (data.getClipData() != null) {
                            final int count = data.getClipData().getItemCount();
                            int currentItem = 0;
                            final ArrayList<String> paths = new ArrayList<>();
                            while (currentItem < count) {
                                final Uri currentUri = data.getClipData().getItemAt(currentItem).getUri();
                                String path = FileUtils.getPath(currentUri, FilePickerDelegate.this.activity);
                                if (path == null) {
                                    path = FileUtils.getUriFromRemote(FilePickerDelegate.this.activity, currentUri);
                                }
                                paths.add(path);
                                Log.i(FilePickerDelegate.TAG, "[MultiFilePick] File #" + currentItem + " - URI: " + currentUri.getPath());
                                currentItem++;
                            }
                            if (paths.size() > 1) {
                                finishWithSuccess(paths);
                            } else {
                                finishWithSuccess(paths.get(0));
                            }
                        } else if (data.getData() != null) {
                            final Uri uri = data.getData();
                            Log.i(FilePickerDelegate.TAG, "[SingleFilePick] File URI:" + uri.toString());
                            String fullPath = FileUtils.getPath(uri, FilePickerDelegate.this.activity);

                            if (fullPath == null) {
                                fullPath = FileUtils.getUriFromRemote(FilePickerDelegate.this.activity, uri);
                            }

                            if (fullPath != null) {
                                Log.i(FilePickerDelegate.TAG, "Absolute file path:" + fullPath);
                                finishWithSuccess(fullPath);
                            } else {
                                finishWithError("unknown_path", "Failed to retrieve path.");
                            }
                        } else {
                            finishWithError("unknown_activity", "Unknown activity error, please fill an issue.");
                        }
                    } else {
                        finishWithError("unknown_activity", "Unknown activity error, please fill an issue.");
                    }
                }
            }).start();

            return true;

        } else if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
            Log.i(TAG, "User cancelled the picker request");
            finishWithSuccess(null);
            return true;
        } else if (requestCode == REQUEST_CODE) {
            finishWithError("unknown_activity", "Unknown activity error, please fill an issue.");
        }
        return false;
    }


    private boolean setPendingMethodCallAndResult(final MethodChannel.Result result) {
        if (this.pendingResult != null) {
            return false;
        }
        this.pendingResult = result;
        return true;
    }

    private static void finishWithAlreadyActiveError(final MethodChannel.Result result) {
        result.error("already_active", "File picker is already active", null);
    }

    private void startFileExplorer() {
        final Intent intent;

        intent = new Intent(Intent.ACTION_GET_CONTENT);
        final Uri uri = Uri.parse(Environment.getExternalStorageDirectory().getPath() + File.separator);
        intent.setDataAndType(uri, this.type);
        intent.setType(this.type);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, this.isMultipleSelection);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (intent.resolveActivity(this.activity.getPackageManager()) != null) {
            this.activity.startActivityForResult(intent, REQUEST_CODE);
        } else {
            Log.e(TAG, "Can't find a valid activity to handle the request. Make sure you've a file explorer installed.");
            finishWithError("invalid_format_type", "Can't handle the provided file type.");
        }
    }

    @SuppressWarnings("deprecation")
    public void startFileExplorer(final String type, final boolean isMultipleSelection, final MethodChannel.Result result) {

        if (!this.setPendingMethodCallAndResult(result)) {
            finishWithAlreadyActiveError(result);
            return;
        }

        this.type = type;
        this.isMultipleSelection = isMultipleSelection;

        this.startFileExplorer();
    }

    private void finishWithSuccess(final Object data) {
        // Temporary fix, remove this null-check after Flutter Engine 1.14 has landed on stable
        if (this.pendingResult != null) {
            this.pendingResult.success(data);
            this.clearPendingResult();
        }
    }

    private void finishWithError(final String errorCode, final String errorMessage) {
        if (this.pendingResult == null) {
            return;
        }
        this.pendingResult.error(errorCode, errorMessage, null);
        this.clearPendingResult();
    }

    private void clearPendingResult() {
        this.pendingResult = null;
    }
}
