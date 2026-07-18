package com.skypiea.videopur4k;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.core.content.FileProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.GaussianBlur;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.LanczosResample;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class MainActivity extends Activity {

    private static final int REQUEST_VIDEO = 501;
    private static final long PROGRESS_INTERVAL_MS = 500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ProgressHolder progressHolder = new ProgressHolder();

    private VideoView videoPreview;
    private TextView fileNameText;
    private TextView videoInfoText;
    private TextView modeDescriptionText;
    private TextView statusText;
    private TextView progressText;
    private ProgressBar progressBar;
    private Spinner presetSpinner;
    private Spinner resolutionSpinner;
    private Button selectButton;
    private Button exportButton;
    private Button cancelButton;
    private Button playOriginalButton;
    private Button playResultButton;
    private Button shareButton;
    private LinearLayout resultPanel;

    private Uri selectedVideoUri;
    private Uri publishedVideoUri;
    private File temporaryOutputFile;
    private Transformer transformer;
    private boolean exportRunning;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!exportRunning || transformer == null) {
                return;
            }

            int state = transformer.getProgress(progressHolder);
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                int progress = Math.max(0, Math.min(100, progressHolder.progress));
                progressBar.setProgress(progress);
                progressText.setText(progress + " %");
                statusText.setText("Amélioration en cours…");
            } else {
                statusText.setText("Préparation du traitement…");
            }
            mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        configureSpinners();
        configureActions();
    }

    private void bindViews() {
        videoPreview = findViewById(R.id.videoPreview);
        fileNameText = findViewById(R.id.fileNameText);
        videoInfoText = findViewById(R.id.videoInfoText);
        modeDescriptionText = findViewById(R.id.modeDescriptionText);
        statusText = findViewById(R.id.statusText);
        progressText = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
        presetSpinner = findViewById(R.id.presetSpinner);
        resolutionSpinner = findViewById(R.id.resolutionSpinner);
        selectButton = findViewById(R.id.selectButton);
        exportButton = findViewById(R.id.exportButton);
        cancelButton = findViewById(R.id.cancelButton);
        playOriginalButton = findViewById(R.id.playOriginalButton);
        playResultButton = findViewById(R.id.playResultButton);
        shareButton = findViewById(R.id.shareButton);
        resultPanel = findViewById(R.id.resultPanel);
    }

    private void configureSpinners() {
        String[] presets = {
                "Rapide — correction légère",
                "Propre — nettoyage équilibré",
                "Maximum — correction renforcée"
        };
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                presets
        );
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(presetAdapter);
        presetSpinner.setSelection(1);

        String[] resolutions = {
                "720p — fichier léger",
                "1080p — Full HD",
                "2K — 2560 × 1440",
                "4K — 3840 × 2160"
        };
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                resolutions
        );
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setSelection(1);

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateModeDescription(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateModeDescription(1);
            }
        });
    }

    private void updateModeDescription(int preset) {
        if (preset == 0) {
            modeDescriptionText.setText("Traitement plus rapide : contraste, luminosité, couleurs et agrandissement Lanczos.");
        } else if (preset == 2) {
            modeDescriptionText.setText("Nettoyage plus fort, contraste renforcé et couleurs plus présentes. Le téléphone peut chauffer davantage.");
        } else {
            modeDescriptionText.setText("Nettoyage équilibré, détails renforcés et couleurs corrigées sans effet excessif.");
        }
    }

    private void configureActions() {
        selectButton.setOnClickListener(v -> openVideoPicker());
        exportButton.setOnClickListener(v -> startExport());
        cancelButton.setOnClickListener(v -> cancelExport());
        playOriginalButton.setOnClickListener(v -> playVideo(selectedVideoUri));
        playResultButton.setOnClickListener(v -> playVideo(publishedVideoUri));
        shareButton.setOnClickListener(v -> shareResult());
    }

    private void openVideoPicker() {
        if (exportRunning) {
            Toast.makeText(this, "Annule d’abord le traitement en cours.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        selectedVideoUri = data.getData();
        publishedVideoUri = null;
        resultPanel.setVisibility(View.GONE);
        progressBar.setProgress(0);
        progressText.setText("0 %");
        statusText.setText("Vidéo prête");

        try {
            int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(selectedVideoUri, takeFlags);
        } catch (SecurityException ignored) {
            // Certains gestionnaires de fichiers ne proposent pas de permission persistante.
        }

        fileNameText.setText(resolveDisplayName(selectedVideoUri));
        videoInfoText.setText(readVideoInformation(selectedVideoUri));
        exportButton.setEnabled(true);
        playVideo(selectedVideoUri);
    }

    private void playVideo(Uri uri) {
        if (uri == null) {
            return;
        }
        videoPreview.stopPlayback();
        videoPreview.setVideoURI(uri);
        videoPreview.setOnPreparedListener(player -> {
            player.setLooping(true);
            videoPreview.start();
        });
        videoPreview.setOnErrorListener((player, what, extra) -> {
            Toast.makeText(this, "La prévisualisation n’est pas disponible pour ce format.", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void startExport() {
        if (selectedVideoUri == null || exportRunning) {
            return;
        }

        File outputDirectory = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (outputDirectory == null && getFilesDir() != null) {
            outputDirectory = new File(getFilesDir(), "exports");
        }
        if (outputDirectory == null || (!outputDirectory.exists() && !outputDirectory.mkdirs())) {
            showFailure("Impossible de créer le dossier de sortie.");
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(new Date());
        temporaryOutputFile = new File(outputDirectory, "VideoPur4K_" + timestamp + ".mp4");
        if (temporaryOutputFile.exists() && !temporaryOutputFile.delete()) {
            showFailure("Impossible de remplacer l’ancien fichier temporaire.");
            return;
        }

        int preset = presetSpinner.getSelectedItemPosition();
        int[] target = targetResolution(resolutionSpinner.getSelectedItemPosition());
        List<Effect> videoEffects = createVideoEffects(preset, target[0], target[1]);
        Effects effects = new Effects(Collections.emptyList(), videoEffects);
        EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(MediaItem.fromUri(selectedVideoUri))
                .setEffects(effects)
                .build();

        Transformer.Listener listener = new Transformer.Listener() {
            @Override
            public void onCompleted(Composition composition, ExportResult exportResult) {
                exportRunning = false;
                mainHandler.removeCallbacks(progressRunnable);
                progressBar.setProgress(100);
                progressText.setText("100 %");
                statusText.setText("Enregistrement dans la galerie…");
                publishCompletedVideo();
            }

            @Override
            public void onError(Composition composition, ExportResult exportResult, ExportException exception) {
                exportRunning = false;
                mainHandler.removeCallbacks(progressRunnable);
                finishExportUi();
                String message = "Échec du traitement. Essaie une résolution plus basse, par exemple 1080p.";
                if (exception.getMessage() != null && !exception.getMessage().isEmpty()) {
                    message += "\n" + exception.getMessage();
                }
                showFailure(message);
            }
        };

        transformer = new Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build();

        exportRunning = true;
        publishedVideoUri = null;
        resultPanel.setVisibility(View.GONE);
        progressBar.setProgress(0);
        progressText.setText("0 %");
        statusText.setText("Préparation du traitement…");
        selectButton.setEnabled(false);
        exportButton.setEnabled(false);
        cancelButton.setEnabled(true);
        presetSpinner.setEnabled(false);
        resolutionSpinner.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            transformer.start(editedMediaItem, temporaryOutputFile.getAbsolutePath());
            mainHandler.post(progressRunnable);
        } catch (RuntimeException exception) {
            exportRunning = false;
            finishExportUi();
            showFailure("Le traitement n’a pas pu démarrer : " + exception.getMessage());
        }
    }

    private List<Effect> createVideoEffects(int preset, int width, int height) {
        List<Effect> effects = new ArrayList<>();

        if (preset == 1) {
            effects.add(new GaussianBlur(0.35f));
            effects.add(new Contrast(0.09f));
            effects.add(new Brightness(0.012f));
            effects.add(new HslAdjustment.Builder().adjustSaturation(4f).adjustLightness(1f).build());
        } else if (preset == 2) {
            effects.add(new GaussianBlur(0.52f));
            effects.add(new Contrast(0.15f));
            effects.add(new Brightness(0.018f));
            effects.add(new HslAdjustment.Builder().adjustSaturation(7f).adjustLightness(1.5f).build());
        } else {
            effects.add(new Contrast(0.055f));
            effects.add(new Brightness(0.008f));
            effects.add(new HslAdjustment.Builder().adjustSaturation(2.5f).adjustLightness(0.5f).build());
        }

        effects.add(LanczosResample.scaleToFitWithFlexibleOrientation(width, height));
        return effects;
    }

    private int[] targetResolution(int selectedPosition) {
        switch (selectedPosition) {
            case 0:
                return new int[]{1280, 720};
            case 2:
                return new int[]{2560, 1440};
            case 3:
                return new int[]{3840, 2160};
            case 1:
            default:
                return new int[]{1920, 1080};
        }
    }

    private void cancelExport() {
        if (!exportRunning || transformer == null) {
            return;
        }
        transformer.cancel();
        exportRunning = false;
        mainHandler.removeCallbacks(progressRunnable);
        if (temporaryOutputFile != null && temporaryOutputFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            temporaryOutputFile.delete();
        }
        finishExportUi();
        statusText.setText("Traitement annulé");
        progressBar.setProgress(0);
        progressText.setText("0 %");
    }

    private void publishCompletedVideo() {
        new Thread(() -> {
            try {
                Uri resultUri = saveToGalleryOrReturnFile(temporaryOutputFile);
                runOnUiThread(() -> {
                    publishedVideoUri = resultUri;
                    finishExportUi();
                    statusText.setText("Terminé — vidéo enregistrée");
                    resultPanel.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "La vidéo améliorée est dans la galerie.", Toast.LENGTH_LONG).show();
                    playVideo(publishedVideoUri);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    finishExportUi();
                    showFailure("La vidéo a été traitée, mais son enregistrement a échoué : " + exception.getMessage());
                });
            }
        }, "video-publish").start();
    }

    private Uri saveToGalleryOrReturnFile(File source) throws Exception {
        if (source == null || !source.exists() || source.length() == 0L) {
            throw new IllegalStateException("Le fichier exporté est vide.");
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", source);
        }

        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Video Pur 4K");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        Uri destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (destination == null) {
            throw new IllegalStateException("La galerie n’a pas accepté le fichier.");
        }

        boolean success = false;
        try (InputStream input = new FileInputStream(source);
             OutputStream output = resolver.openOutputStream(destination, "w")) {
            if (output == null) {
                throw new IllegalStateException("Impossible d’ouvrir le fichier de destination.");
            }
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            success = true;
        } finally {
            if (!success) {
                resolver.delete(destination, null, null);
            }
        }

        values.clear();
        values.put(MediaStore.Video.Media.IS_PENDING, 0);
        resolver.update(destination, values, null, null);
        //noinspection ResultOfMethodCallIgnored
        source.delete();
        return destination;
    }

    private void finishExportUi() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        selectButton.setEnabled(true);
        exportButton.setEnabled(selectedVideoUri != null);
        cancelButton.setEnabled(false);
        presetSpinner.setEnabled(true);
        resolutionSpinner.setEnabled(true);
    }

    private void shareResult() {
        if (publishedVideoUri == null) {
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("video/mp4");
        shareIntent.putExtra(Intent.EXTRA_STREAM, publishedVideoUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Partager la vidéo améliorée"));
    }

    private String resolveDisplayName(Uri uri) {
        String name = "Vidéo sélectionnée";
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Le nom générique reste affiché.
        }
        return name;
    }

    private String readVideoInformation(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            int width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rotation = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            long durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (rotation == 90 || rotation == 270) {
                int temp = width;
                width = height;
                height = temp;
            }
            String resolution = width > 0 && height > 0 ? width + " × " + height : "résolution inconnue";
            return resolution + " • " + formatDuration(durationMs) + " • audio conservé";
        } catch (Exception exception) {
            return "Informations non disponibles • audio conservé";
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // Aucun traitement nécessaire.
            }
        }
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.FRANCE, "%d:%02d", minutes, seconds);
    }

    private int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private void showFailure(String message) {
        statusText.setText("Erreur");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(progressRunnable);
        if (exportRunning && transformer != null) {
            transformer.cancel();
        }
        videoPreview.stopPlayback();
        super.onDestroy();
    }
}
