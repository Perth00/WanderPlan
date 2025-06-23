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
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AddActivityActivity extends AppCompatActivity {
    
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
    private String uploadedImageUrl;
    
    private int tripId;
    private int activityId = -1; // -1 means new activity
    private Trip currentTrip;
    private TripActivity currentActivity;
    
    // Modern replacement for startActivityForResult
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_activity);
        
        // Get parameters from intent
        tripId = getIntent().getIntExtra("trip_id", -1);
        activityId = getIntent().getIntExtra("activity_id", -1);
        
        if (tripId == -1) {
            Toast.makeText(this, "Invalid trip ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        setupViewModel();
        setupImagePickerLauncher();
        setupDateTimeFormatters();
        setupClickListeners();
        setupToolbar();
        setupBackPressHandler();
        loadTripData();
        
        if (activityId != -1) {
            loadActivityData();
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
        // Modern replacement for startActivityForResult
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        loadImagePreview(selectedImageUri.toString());
                        uploadedImageUrl = null; // Clear previous uploaded URL
                    }
                }
            });
    }
    
    private void setupBackPressHandler() {
        // Modern replacement for onBackPressed
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
        
        imagePreview.setOnClickListener(v -> {
            if (selectedImageUri != null || uploadedImageUrl != null) {
                selectImage(); // Allow changing image
            }
        });
    }
    
    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (activityId == -1) {
                getSupportActionBar().setTitle("Add Activity");
            } else {
                getSupportActionBar().setTitle("Edit Activity");
            }
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    private void loadTripData() {
        // CRITICAL FIX: Use direct repository access to avoid LiveData observer leaks
        new Thread(() -> {
            try {
                TripRepository repository = TripRepository.getInstance(this);
                List<com.example.mobiledegreefinalproject.database.Trip> trips = repository.getAllTripsSync();
                
                for (com.example.mobiledegreefinalproject.database.Trip trip : trips) {
                    if (trip.getId() == tripId) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                currentTrip = trip;
                                Log.d(TAG, "Trip data loaded: " + trip.getTitle());
                            }
                        });
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading trip data", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(AddActivityActivity.this, "Error loading trip data", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
    
    private void loadActivityData() {
        // CRITICAL FIX: Use direct repository access to avoid LiveData observer leaks
        new Thread(() -> {
            try {
                TripRepository repository = TripRepository.getInstance(this);
                List<TripActivity> activities = repository.getActivitiesForTripSync(tripId);
                
                for (TripActivity activity : activities) {
                    if (activity.getId() == activityId) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                currentActivity = activity;
                                populateFields(activity);
                                Log.d(TAG, "Activity data loaded: " + activity.getTitle());
                            }
                        });
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading activity data", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(AddActivityActivity.this, "Error loading activity data", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
    
    private void populateFields(TripActivity activity) {
        editActivityTitle.setText(activity.getTitle());
        editDescription.setText(activity.getDescription());
        editLocation.setText(activity.getLocation());
        
        selectedDateTime.setTimeInMillis(activity.getDateTime());
        updateDateTimeDisplays();
        
        // Load existing image if available
        if (activity.hasImage()) {
            uploadedImageUrl = activity.getDisplayImagePath();
            loadImagePreview(uploadedImageUrl);
        }
    }
    
    private void setDefaultDateTime() {
        // Set default time to 9:00 AM
        selectedDateTime.set(Calendar.HOUR_OF_DAY, 9);
        selectedDateTime.set(Calendar.MINUTE, 0);
        
        if (currentTrip != null) {
            // Set to trip start date
            selectedDateTime.setTimeInMillis(currentTrip.getStartDate());
            selectedDateTime.set(Calendar.HOUR_OF_DAY, 9);
            selectedDateTime.set(Calendar.MINUTE, 0);
        }
        
        updateDateTimeDisplays();
    }
    
    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                selectedDateTime.set(year, month, dayOfMonth);
                updateDateTimeDisplays();
            },
            selectedDateTime.get(Calendar.YEAR),
            selectedDateTime.get(Calendar.MONTH),
            selectedDateTime.get(Calendar.DAY_OF_MONTH)
        );
        
        // Set date range to trip dates if available
        if (currentTrip != null) {
            datePickerDialog.getDatePicker().setMinDate(currentTrip.getStartDate());
            datePickerDialog.getDatePicker().setMaxDate(currentTrip.getEndDate());
        }
        
        datePickerDialog.show();
    }
    
    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
            this,
            (view, hourOfDay, minute) -> {
                selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedDateTime.set(Calendar.MINUTE, minute);
                updateDateTimeDisplays();
            },
            selectedDateTime.get(Calendar.HOUR_OF_DAY),
            selectedDateTime.get(Calendar.MINUTE),
            false
        );
        
        timePickerDialog.show();
    }
    
    private void updateDateTimeDisplays() {
        textSelectedDate.setText(dateFormat.format(selectedDateTime.getTime()));
        textSelectedTime.setText(timeFormat.format(selectedDateTime.getTime()));
    }
    
    private void selectImage() {
        // Fixed: Create intent properly to avoid data clearing warning
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        imagePickerLauncher.launch(intent); // Use modern launcher
    }
    

    
    private void loadImagePreview(String imageUri) {
        imagePreview.setVisibility(View.VISIBLE);
        Glide.with(this)
            .load(imageUri)
            .centerCrop()
            .placeholder(R.drawable.ic_trips)
            .error(R.drawable.ic_trips)
            .into(imagePreview);
    }
    
    private void saveActivity() {
        String title = editActivityTitle.getText().toString().trim();
        String description = editDescription.getText().toString().trim();
        String location = editLocation.getText().toString().trim();
        
        // Validate inputs
        if (TextUtils.isEmpty(title)) {
            editActivityTitle.setError("Activity title is required");
            editActivityTitle.requestFocus();
            return;
        }
        
        // CRITICAL FIX: Validate that we have valid trip data
        if (currentTrip == null) {
            Log.e(TAG, "Current trip is null, cannot save activity");
            Toast.makeText(this, "Trip data not loaded, please try again", Toast.LENGTH_SHORT).show();
            loadTripData(); // Try to reload trip data
            return;
        }
        
        // Add comprehensive validation
        if (tripId <= 0) {
            Log.e(TAG, "Invalid trip ID: " + tripId);
            Toast.makeText(this, "Invalid trip data, please restart", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedDateTime == null) {
            Log.e(TAG, "Selected date/time is null");
            Toast.makeText(this, "Please select a date and time", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "All validations passed, proceeding with save");
        
        // Add Firebase status logging
        checkFirebaseStatus();
        
        // Calculate day number
        int dayNumber = calculateDayNumber(selectedDateTime.getTimeInMillis(), currentTrip.getStartDate());
        
        // Show loading
        setLoadingState(true);
        
        // CRITICAL FIX: Add flag to prevent double saves
        if (btnSaveActivity.getTag() != null && btnSaveActivity.getTag().equals("saving")) {
            Log.w(TAG, "Save already in progress, ignoring duplicate request");
            return;
        }
        btnSaveActivity.setTag("saving");
        
        try {
            if (selectedImageUri != null) {
                uploadImageAndSaveActivity(title, description, location, dayNumber);
            } else {
                saveActivityToDatabase(title, description, location, dayNumber, uploadedImageUrl);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in saveActivity", e);
            setLoadingState(false);
            btnSaveActivity.setTag(null); // Reset flag
            Toast.makeText(this, "Error saving activity: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private int calculateDayNumber(long activityDate, long tripStartDate) {
        long diffInMillis = activityDate - tripStartDate;
        int dayNumber = (int) (diffInMillis / TimeUnit.DAYS.toMillis(1)) + 1;
        return Math.max(1, dayNumber);
    }
    
    private void uploadImageAndSaveActivity(String title, String description, String location, int dayNumber) {
        UserManager userManager = UserManager.getInstance(this);
        
        if (!userManager.isLoggedIn()) {
            // For guest users: copy image to internal storage for later Firebase upload
            copyImageToInternalStorage(title, description, location, dayNumber);
            return;
        }
        
        // CRITICAL FIX: Add timeout protection and memory leak prevention
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing/destroyed, skipping image upload");
            setLoadingState(false);
            btnSaveActivity.setTag(null); // Reset flag
            return;
        }
        
        try {
            // Compress and upload image with better error handling
            Glide.with(this)
                .asBitmap()
                .load(selectedImageUri)
                .override(512, 512) // Reasonable size for better performance
                .timeout(20000) // 20 second timeout
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // Check if activity is still alive before proceeding
                        if (!isFinishing() && !isDestroyed()) {
                            uploadBitmapToFirebase(resource, title, description, location, dayNumber);
                        } else {
                            Log.w(TAG, "Activity destroyed during image processing, saving locally");
                            btnSaveActivity.setTag(null); // Reset flag
                            saveActivityToDatabase(title, description, location, dayNumber, null);
                        }
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // Image loading was cancelled or failed
                        Log.w(TAG, "Image loading cancelled, saving activity without image");
                        if (!isFinishing() && !isDestroyed()) {
                            saveActivityToDatabase(title, description, location, dayNumber, null);
                        } else {
                            btnSaveActivity.setTag(null); // Reset flag
                        }
                    }
                    
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        Log.e(TAG, "Image loading failed, saving activity without image");
                        if (!isFinishing() && !isDestroyed()) {
                            saveActivityToDatabase(title, description, location, dayNumber, null);
                        } else {
                            btnSaveActivity.setTag(null); // Reset flag
                        }
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up image upload", e);
            btnSaveActivity.setTag(null); // Reset flag
            // Fallback to saving without image
            saveActivityToDatabase(title, description, location, dayNumber, null);
        }
    }
    
    private void uploadBitmapToFirebase(Bitmap bitmap, String title, String description, String location, int dayNumber) {
        // CRITICAL FIX: Check activity state before Firebase operations
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity finishing/destroyed, saving locally without image upload");
            btnSaveActivity.setTag(null); // Reset flag
            saveActivityToDatabase(title, description, location, dayNumber, null);
            return;
        }
        
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteStream); // Better quality but still compressed
            byte[] data = byteStream.toByteArray();
            
            // Limit file size to prevent crashes
            if (data.length > 1024 * 1024 * 2) { // 2MB limit
                Log.w(TAG, "Image too large (" + (data.length / 1024 / 1024) + "MB), saving without upload");
                saveActivityToDatabase(title, description, location, dayNumber, null);
                return;
            }
            
            String fileName = "activity_" + System.currentTimeMillis() + "_" + title.replaceAll("[^a-zA-Z0-9]", "") + ".jpg";
            StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("activity_images")
                .child(fileName);
            
            // CRITICAL FIX: Add timeout protection for Firebase upload to prevent hanging
            final boolean[] uploadCompleted = {false};
            android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            
            Runnable timeoutRunnable = () -> {
                synchronized (uploadCompleted) {
                    if (!uploadCompleted[0]) {
                        uploadCompleted[0] = true;
                        Log.w(TAG, "Firebase upload timeout, saving without image");
                        btnSaveActivity.setTag(null); // Reset flag
                        if (!isFinishing() && !isDestroyed()) {
                            saveActivityToDatabase(title, description, location, dayNumber, null);
                        }
                    }
                }
            };
            
            // Set 25-second timeout for upload
            timeoutHandler.postDelayed(timeoutRunnable, 25000);
            
            storageRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> {
                    synchronized (uploadCompleted) {
                        if (uploadCompleted[0]) {
                            Log.w(TAG, "Upload completed but timeout already triggered");
                            return;
                        }
                        
                        // Double check activity state before continuing
                        if (isFinishing() || isDestroyed()) {
                            uploadCompleted[0] = true;
                            timeoutHandler.removeCallbacks(timeoutRunnable);
                            Log.w(TAG, "Activity destroyed after upload success, not saving");
                            btnSaveActivity.setTag(null); // Reset flag
                            return;
                        }
                        
                        storageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                synchronized (uploadCompleted) {
                                    if (!uploadCompleted[0] && !isFinishing() && !isDestroyed()) {
                                        uploadCompleted[0] = true;
                                        timeoutHandler.removeCallbacks(timeoutRunnable);
                                        String imageUrl = uri.toString();
                                        saveActivityToDatabase(title, description, location, dayNumber, imageUrl);
                                    } else {
                                        btnSaveActivity.setTag(null); // Reset flag
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {
                                synchronized (uploadCompleted) {
                                    if (!uploadCompleted[0]) {
                                        uploadCompleted[0] = true;
                                        timeoutHandler.removeCallbacks(timeoutRunnable);
                                        Log.e(TAG, "Error getting download URL", e);
                                        btnSaveActivity.setTag(null); // Reset flag
                                        if (!isFinishing() && !isDestroyed()) {
                                            saveActivityToDatabase(title, description, location, dayNumber, null);
                                        }
                                    }
                                }
                            });
                    }
                })
                .addOnFailureListener(e -> {
                    synchronized (uploadCompleted) {
                        if (!uploadCompleted[0]) {
                            uploadCompleted[0] = true;
                            timeoutHandler.removeCallbacks(timeoutRunnable);
                            Log.e(TAG, "Error uploading image", e);
                            btnSaveActivity.setTag(null); // Reset flag
                            if (!isFinishing() && !isDestroyed()) {
                                saveActivityToDatabase(title, description, location, dayNumber, null);
                            }
                        }
                    }
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error processing image for Firebase", e);
            btnSaveActivity.setTag(null); // Reset flag
            if (!isFinishing() && !isDestroyed()) {
                saveActivityToDatabase(title, description, location, dayNumber, null);
            }
        }
    }
    
    /**
     * Copy image to internal storage for guest users (so it's available for later Firebase upload)
     */
    private void copyImageToInternalStorage(String title, String description, String location, int dayNumber) {
        Log.d(TAG, "Copying guest image to internal storage for: " + title);
        
        // Use Glide to load and save the image to internal storage
        Glide.with(this)
                .asBitmap()
                .load(selectedImageUri)
                .override(1024, 1024) // Reasonable size
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                        // Save bitmap to internal storage
                        String savedPath = saveBitmapToInternalStorage(bitmap, title);
                        if (savedPath != null) {
                            Log.d(TAG, "✅ Guest image saved to internal storage: " + savedPath);
                            saveActivityToDatabase(title, description, location, dayNumber, savedPath);
                        } else {
                            Log.w(TAG, "⚠️ Failed to save guest image, saving without image");
                            saveActivityToDatabase(title, description, location, dayNumber, null);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        Log.w(TAG, "⚠️ Glide load cleared for guest image, saving without image");
                        saveActivityToDatabase(title, description, location, dayNumber, null);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        Log.w(TAG, "⚠️ Failed to load guest image, saving without image");
                        saveActivityToDatabase(title, description, location, dayNumber, null);
                    }
                });
    }
    
    /**
     * Save bitmap to internal storage and return file path
     */
    private String saveBitmapToInternalStorage(Bitmap bitmap, String activityTitle) {
        try {
            // Create images directory in internal storage
            File imagesDir = new File(getFilesDir(), "activity_images");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }
            
            // Create unique filename
            String filename = "activity_" + System.currentTimeMillis() + "_" + 
                            activityTitle.replaceAll("[^a-zA-Z0-9]", "") + ".jpg";
            File imageFile = new File(imagesDir, filename);
            
            // Save bitmap to file
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream); // Good quality
            outputStream.flush();
            outputStream.close();
            
            String savedPath = imageFile.getAbsolutePath();
            Log.d(TAG, "📷 Bitmap saved to internal storage: " + savedPath);
            return savedPath;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving bitmap to internal storage", e);
            return null;
        }
    }

    private void saveActivityToDatabase(String title, String description, String location, int dayNumber, String imageUrl) {
        // CRITICAL FIX: Check activity state before database operations
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing/destroyed, skipping database save");
            btnSaveActivity.setTag(null); // Reset flag
            return;
        }
        
        try {
            Log.d(TAG, "Starting to save activity to Firebase as JSON");
            Log.d(TAG, "Activity details - Title: " + title + ", Location: " + location + ", DayNumber: " + dayNumber + ", ImageURL: " + imageUrl);
            Log.d(TAG, "Trip ID: " + tripId + ", DateTime: " + selectedDateTime.getTimeInMillis());
            
            // Create activity JSON data
            TripActivity activity = new TripActivity();
            activity.setTripId(tripId);
            activity.setTitle(title);
            activity.setDescription(description);
            activity.setLocation(location);
            activity.setDateTime(selectedDateTime.getTimeInMillis());
            activity.setDayNumber(dayNumber);
            activity.setTimeString(timeFormat.format(selectedDateTime.getTime()));
            activity.setImageUrl(imageUrl);
            activity.setCreatedAt(System.currentTimeMillis());
            activity.setUpdatedAt(System.currentTimeMillis());
            
            if (activityId != -1) {
                activity.setId(activityId);
            }
            
            UserManager userManager = UserManager.getInstance(this);
            
            if (userManager.isLoggedIn()) {
                // FIREBASE-ONLY MODE: For logged-in users, save directly to Firebase only
                Log.d(TAG, "User logged in - saving DIRECTLY to Firebase (no local database)");
                
                DataSyncService syncService = new DataSyncService(AddActivityActivity.this);
                syncService.saveActivityAsJson(activity, currentTrip, new DataSyncService.OnActivitySaveListener() {
                    @Override
                    public void onSuccess(String firebaseId) {
                        Log.d(TAG, "✓ Activity saved to Firebase JSON with ID: " + firebaseId);
                        activity.setFirebaseId(firebaseId);
                        
                        // CRITICAL FIX: Add small delay to ensure Firebase data is propagated
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            // Show success dialog for Firebase-only save
                            showSuccessDialogAfterAllSaves();
                        }, 300); // 300ms delay to ensure Firebase propagation
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "*** ERROR: Firebase save failed: " + error);
                        
                        try {
                            if (!isFinishing() && !isDestroyed()) {
                                runOnUiThread(() -> {
                                    try {
                                        if (!isFinishing() && !isDestroyed()) {
                                            setLoadingState(false);
                                            btnSaveActivity.setTag(null);
                                            
                                            String userMessage = "Failed to save activity to cloud";
                                            if (error.contains("timeout") || error.contains("network")) {
                                                userMessage = "Network error - please try again";
                                            }
                                            
                                            Toast.makeText(AddActivityActivity.this, userMessage, Toast.LENGTH_LONG).show();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error in Firebase error UI update", e);
                                        btnSaveActivity.setTag(null);
                                    }
                                });
                            } else {
                                btnSaveActivity.setTag(null);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in Firebase error handling", e);
                            btnSaveActivity.setTag(null);
                        }
                    }
                });
                
            } else {
                // OFFLINE MODE: For offline users, save to local database only
                Log.d(TAG, "User offline - saving to local database only");
                
                TripRepository repository = TripRepository.getInstance(this);
                if (repository == null) {
                    Log.e(TAG, "Repository is null!");
                    btnSaveActivity.setTag(null);
                    setLoadingState(false);
                    Toast.makeText(this, "Database error - please try again", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                TripRepository.OnActivityOperationListener localListener = new TripRepository.OnActivityOperationListener() {
                    @Override
                    public void onSuccess(int savedActivityId) {
                        Log.d(TAG, "✓ Activity saved to local database with ID: " + savedActivityId);
                        activity.setId(savedActivityId);
                        showSuccessDialogAfterAllSaves();
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "*** ERROR: Local database save failed: " + error);
                        
                        try {
                            if (!isFinishing() && !isDestroyed()) {
                                runOnUiThread(() -> {
                                    try {
                                        if (!isFinishing() && !isDestroyed()) {
                                            setLoadingState(false);
                                            btnSaveActivity.setTag(null);
                                            Toast.makeText(AddActivityActivity.this, "Failed to save activity locally", Toast.LENGTH_LONG).show();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error in local error UI update", e);
                                        btnSaveActivity.setTag(null);
                                    }
                                });
                            } else {
                                btnSaveActivity.setTag(null);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in local error handling", e);
                            btnSaveActivity.setTag(null);
                        }
                    }
                };
                
                // Execute the local database save
                Log.d(TAG, "Calling repository to save activity locally");
                if (activityId == -1) {
                    repository.insertActivity(activity, localListener);
                } else {
                    repository.updateActivity(activity, localListener);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Exception in saveActivityToDatabase", e);
            e.printStackTrace(); // Print full stack trace
            btnSaveActivity.setTag(null); // Reset flag
            if (!isFinishing() && !isDestroyed()) {
                runOnUiThread(() -> {
                    try {
                        if (!isFinishing() && !isDestroyed()) {
                            setLoadingState(false);
                            Toast.makeText(AddActivityActivity.this, "Critical error saving activity: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception uiError) {
                        Log.e(TAG, "Error showing critical error message", uiError);
                    }
                });
            }
        }
    }
    
    /**
     * Show success dialog after all database operations complete
     */
    private void showSuccessDialogAfterAllSaves() {
        try {
            if (!isFinishing() && !isDestroyed()) {
                // Add small delay to ensure all database operations are truly complete
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        if (!isFinishing() && !isDestroyed()) {
                            setLoadingState(false);
                            btnSaveActivity.setTag(null);
                            
                            String message = activityId == -1 ? 
                                getString(R.string.activity_created_successfully) : 
                                getString(R.string.activity_updated_successfully);
                            
                            SuccessDialogHelper.showSuccessDialog(
                                AddActivityActivity.this,
                                message,
                                () -> {
                                    // CRITICAL FIX: Ensure result triggers immediate refresh
                                    setResult(RESULT_OK);
                                    
                                    // Add small delay before finishing to ensure stable transition
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            finish();
                                        }
                                    }, 150); // 150ms delay for stable transition
                                }
                            );
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing success dialog", e);
                        setResult(RESULT_OK);
                        if (!isFinishing() && !isDestroyed()) {
                            finish();
                        }
                    }
                }, 200); // 200ms delay to ensure database operations complete
            } else {
                setResult(RESULT_OK);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in showSuccessDialogAfterAllSaves", e);
            setResult(RESULT_OK);
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }
    }
    
    private void checkFirebaseStatus() {
        try {
            UserManager userManager = UserManager.getInstance(this);
            Log.d(TAG, "=== FIREBASE STATUS CHECK ===");
            Log.d(TAG, "User logged in: " + (userManager != null ? userManager.isLoggedIn() : "UserManager is null"));
            
            if (userManager != null && userManager.isLoggedIn()) {
                Log.d(TAG, "User email: " + userManager.getUserEmail());
                Log.d(TAG, "User name: " + userManager.getUserName());
                
                // Check trip Firebase sync status
                if (currentTrip != null) {
                    Log.d(TAG, "Current trip Firebase ID: " + currentTrip.getFirebaseId());
                    Log.d(TAG, "Trip synced to Firebase: " + (currentTrip.getFirebaseId() != null && !currentTrip.getFirebaseId().isEmpty()));
                } else {
                    Log.w(TAG, "Current trip is null for Firebase status check");
                }
            } else {
                Log.d(TAG, "User not logged in - will save locally only");
            }
            
            // Check network connectivity
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                Log.d(TAG, "Network connected: " + isConnected);
                if (activeNetwork != null) {
                    Log.d(TAG, "Network type: " + activeNetwork.getTypeName());
                }
            }
            
            Log.d(TAG, "=== END FIREBASE STATUS ===");
        } catch (Exception e) {
            Log.e(TAG, "Error checking Firebase status", e);
        }
    }

    private void setLoadingState(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveActivity.setEnabled(!loading);
        btnSelectDate.setEnabled(!loading);
        btnSelectTime.setEnabled(!loading);
        btnSelectImage.setEnabled(!loading);
        editActivityTitle.setEnabled(!loading);
        editDescription.setEnabled(!loading);
        editLocation.setEnabled(!loading);
    }
    
    // CRITICAL FIX: Add lifecycle management to prevent crashes
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called - activity may be finishing");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() called - cleaning up resources");
        // Clear any Glide requests to prevent memory leaks
        try {
            if (!isDestroyed()) {
                Glide.with(this).clear(imagePreview);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error clearing Glide requests", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() called - final cleanup");
        
        // CRITICAL FIX: Clean up all resources to prevent memory leaks and crashes
        try {
            // Release MediaPlayer resources
            SuccessDialogHelper.releaseMediaPlayer();
            
            // Clear any pending Glide operations
            if (!isDestroyed()) {
                Glide.with(this).clear(imagePreview);
            }
            
            // Clean up any view references
            editActivityTitle = null;
            editDescription = null;
            editLocation = null;
            textSelectedDate = null;
            textSelectedTime = null;
            btnSelectDate = null;
            btnSelectTime = null;
            btnSelectImage = null;
            btnSaveActivity = null;
            imagePreview = null;
            progressBar = null;
            
            // Clear other references
            selectedImageUri = null;
            uploadedImageUrl = null;
            currentTrip = null;
            currentActivity = null;
            viewModel = null;
            
        } catch (Exception e) {
            Log.w(TAG, "Error during cleanup", e);
        }
        
        super.onDestroy();
    }
}