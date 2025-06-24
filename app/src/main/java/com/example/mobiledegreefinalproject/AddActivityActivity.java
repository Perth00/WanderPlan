package com.example.mobiledegreefinalproject;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.example.mobiledegreefinalproject.repository.TripRepository;
import com.example.mobiledegreefinalproject.viewmodel.TripsViewModel;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class AddActivityActivity extends BaseActivity {
    
    private static final String TAG = "AddActivityActivity";
    
    private EditText editActivityTitle;
    private EditText editDescription;
    private EditText editLocation;
    private TextView textSelectedDate;
    private TextView textSelectedTime;
    private Button btnSelectDate;
    private Button btnSelectTime;
    private Button btnSelectImage;
    private Button btnSaveActivity;
    private ImageView imagePreview;
    private ProgressBar progressBar;
    
    private TripsViewModel viewModel;
    private Calendar selectedDateTime;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;
    private Uri selectedImageUri;
    
    private int tripId;
    private Trip currentTrip;
    private boolean isEditMode = false;
    private TripActivity activityToEdit;
    
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_activity);
        
        initViews();

        if (getIntent().hasExtra("activity_to_edit")) {
            isEditMode = true;
            activityToEdit = (TripActivity) getIntent().getSerializableExtra("activity_to_edit");
            if (activityToEdit != null) {
                tripId = activityToEdit.getTripId();
            } else {
                Toast.makeText(this, "Error: Could not load activity to edit.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            isEditMode = false;
        tripId = getIntent().getIntExtra("trip_id", -1);
        }
        
        if (tripId == -1) {
            Toast.makeText(this, "Invalid trip ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        setupViewModel();
        setupImagePickerLauncher();
        setupDateTimeFormatters();
        setupClickListeners();
        setupToolbar();
        setupBackPressHandler();
        loadTripData();
        
        if (isEditMode) {
            populateFields(activityToEdit);
        } else {
            setDefaultDateTime();
        }
    }
    
    private void initViews() {
        editActivityTitle = findViewById(R.id.edit_activity_title);
        editDescription = findViewById(R.id.edit_description);
        editLocation = findViewById(R.id.edit_location);
        textSelectedDate = findViewById(R.id.text_selected_date);
        textSelectedTime = findViewById(R.id.text_selected_time);
        btnSelectDate = findViewById(R.id.btn_select_date);
        btnSelectTime = findViewById(R.id.btn_select_time);
        btnSelectImage = findViewById(R.id.btn_select_image);
        btnSaveActivity = findViewById(R.id.btn_save_activity);
        imagePreview = findViewById(R.id.image_preview);
        progressBar = findViewById(R.id.progress_bar);
    }
    
    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);
    }
    
    private void setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        loadImagePreview(selectedImageUri);
                    }
                }
            });
    }
    
    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }
    
    private void setupDateTimeFormatters() {
        selectedDateTime = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    }
    
    private void setupClickListeners() {
        btnSelectDate.setOnClickListener(v -> showDatePicker());
        btnSelectTime.setOnClickListener(v -> showTimePicker());
        btnSelectImage.setOnClickListener(v -> selectImage());
        btnSaveActivity.setOnClickListener(v -> saveActivity());
        imagePreview.setOnClickListener(v -> selectImage());
    }
    
    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? "Edit Activity" : "Add Activity");
        }
        btnSaveActivity.setText(isEditMode ? "Update Activity" : "Save Activity");
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    private void loadTripData() {
        new Thread(() -> {
            TripRepository repository = TripRepository.getInstance(getApplication());
            currentTrip = repository.getTripByIdSync(tripId);
            if (currentTrip == null) {
                runOnUiThread(() -> Toast.makeText(this, "Error: Could not load trip data.", Toast.LENGTH_SHORT).show());
            } else if (!isEditMode) {
                runOnUiThread(this::setDefaultDateTime);
            }
        }).start();
    }
    
    private void populateFields(TripActivity activity) {
        editActivityTitle.setText(activity.getTitle());
        editDescription.setText(activity.getDescription());
        editLocation.setText(activity.getLocation());
        
        selectedDateTime.setTimeInMillis(activity.getDateTime());
        updateDateTimeDisplays();
        
        if (activity.hasImage()) {
            loadImagePreview(activity.getDisplayImagePath());
        }
    }
    
    private void setDefaultDateTime() {
        if (currentTrip != null) {
            selectedDateTime.setTimeInMillis(currentTrip.getStartDate());
        } else {
            selectedDateTime = Calendar.getInstance();
        }
        updateDateTimeDisplays();
    }
    
    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
            (view, year, month, dayOfMonth) -> {
                selectedDateTime.set(Calendar.YEAR, year);
                selectedDateTime.set(Calendar.MONTH, month);
                selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateDateTimeDisplays();
            },
            selectedDateTime.get(Calendar.YEAR),
            selectedDateTime.get(Calendar.MONTH),
            selectedDateTime.get(Calendar.DAY_OF_MONTH));
        if (currentTrip != null) {
            datePickerDialog.getDatePicker().setMinDate(currentTrip.getStartDate());
            datePickerDialog.getDatePicker().setMaxDate(currentTrip.getEndDate());
        }
        datePickerDialog.show();
    }
    
    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
            (view, hourOfDay, minute) -> {
                selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedDateTime.set(Calendar.MINUTE, minute);
                updateDateTimeDisplays();
            },
            selectedDateTime.get(Calendar.HOUR_OF_DAY),
            selectedDateTime.get(Calendar.MINUTE),
            false);
        timePickerDialog.show();
    }
    
    private void updateDateTimeDisplays() {
        textSelectedDate.setText(dateFormat.format(selectedDateTime.getTime()));
        textSelectedTime.setText(timeFormat.format(selectedDateTime.getTime()));
    }
    
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }
    
    private void loadImagePreview(Object imageSource) {
        Glide.with(this).load(imageSource).centerCrop().into(imagePreview);
        imagePreview.setVisibility(View.VISIBLE);
    }
    
    private void saveActivity() {
        if (TextUtils.isEmpty(editActivityTitle.getText())) {
            editActivityTitle.setError("Title is required");
            return;
        }
        if (currentTrip == null) {
            Toast.makeText(this, "Trip data not loaded yet, please wait.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        long activityTime = selectedDateTime.getTimeInMillis();
        if (activityTime < currentTrip.getStartDate() || activityTime > currentTrip.getEndDate()) {
            Toast.makeText(this, "Activity date must be within the trip's date range.", Toast.LENGTH_LONG).show();
            return;
        }
        
        setLoadingState(true);
        
            if (selectedImageUri != null) {
            handleImageAndSave();
            } else {
            saveData(null);
        }
    }
    
    private void handleImageAndSave() {
        UserManager userManager = UserManager.getInstance(this);
        Glide.with(this).asBitmap().load(selectedImageUri).into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (userManager.isLoggedIn()) {
                    uploadBitmapToFirebase(resource, url -> saveData(url));
                        } else {
                    String localPath = saveBitmapToInternalStorage(resource);
                    saveData(localPath);
                        }
                    }
                    
                    @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
                    
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                setLoadingState(false);
                Toast.makeText(AddActivityActivity.this, "Failed to load image.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void uploadBitmapToFirebase(Bitmap bitmap, ImageUploadCallback callback) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();
        String imageName = "activity_" + System.currentTimeMillis() + ".jpg";
        StorageReference imageRef = FirebaseStorage.getInstance().getReference().child("activity_images/" + imageName);

        imageRef.putBytes(data).addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
            callback.onUrlReady(uri.toString());
        })).addOnFailureListener(e -> {
            setLoadingState(false);
            Toast.makeText(AddActivityActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
    
    private String saveBitmapToInternalStorage(Bitmap bitmap) {
        String fileName = "activity_" + System.currentTimeMillis() + ".jpg";
        File file = new File(getFilesDir(), fileName);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving bitmap", e);
            return null;
        }
    }

    private void saveData(String imagePath) {
        String title = editActivityTitle.getText().toString().trim();
        String description = editDescription.getText().toString().trim();
        String location = editLocation.getText().toString().trim();
        long dateTime = selectedDateTime.getTimeInMillis();
        int dayNumber = calculateDayNumber(dateTime, currentTrip.getStartDate());

        TripActivity activityToSave;
        if (isEditMode) {
            activityToSave = this.activityToEdit;
        } else {
            activityToSave = new TripActivity();
            activityToSave.setTripId(tripId);
        }

        activityToSave.setTitle(title);
        activityToSave.setDescription(description);
        activityToSave.setLocation(location);
        activityToSave.setDateTime(dateTime);
        activityToSave.setDayNumber(dayNumber);
        activityToSave.setTimeString(timeFormat.format(selectedDateTime.getTime()));

        if (imagePath != null) {
            if (imagePath.startsWith("http")) {
                activityToSave.setImageUrl(imagePath);
                activityToSave.setImageLocalPath(null);
            } else {
                activityToSave.setImageLocalPath(imagePath);
                activityToSave.setImageUrl(null);
            }
        }

        if (isEditMode) {
            viewModel.updateActivity(activityToSave, createListener());
                            } else {
            viewModel.insertActivity(activityToSave, createListener());
        }
    }

    private TripRepository.OnActivityOperationListener createListener() {
        return new TripRepository.OnActivityOperationListener() {
            @Override
            public void onSuccess(int activityId) {
                runOnUiThread(() -> {
                    setLoadingState(false);
                    SuccessDialogHelper.showSuccessDialog(AddActivityActivity.this,
                            isEditMode ? "Activity Updated!" : "Activity Added!",
                            () -> {
                                setResult(RESULT_OK);
                                finish();
                            });
                });
                    }
                    
                    @Override
                    public void onError(String error) {
                runOnUiThread(() -> {
                            setLoadingState(false);
                    Toast.makeText(AddActivityActivity.this, "Operation failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        };
    }

    private int calculateDayNumber(long activityDate, long tripStartDate) {
        long diff = activityDate - tripStartDate;
        if (diff < 0) return 0; // Should be caught by validation earlier
        return (int) TimeUnit.MILLISECONDS.toDays(diff) + 1;
    }

    private void setLoadingState(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveActivity.setEnabled(!loading);
        btnSelectDate.setEnabled(!loading);
        btnSelectTime.setEnabled(!loading);
        btnSelectImage.setEnabled(!loading);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SuccessDialogHelper.releaseMediaPlayer();
    }

    interface ImageUploadCallback {
        void onUrlReady(String url);
    }
}