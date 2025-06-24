package com.example.mobiledegreefinalproject;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.repository.TripRepository;
import com.example.mobiledegreefinalproject.viewmodel.TripsViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddTripActivity extends AppCompatActivity {
    
    private static final String TAG = "AddTripActivity";
    
    private EditText editTripTitle;
    private EditText editDestination;
    private TextView textStartDate;
    private TextView textEndDate;
    private Button btnStartDate;
    private Button btnEndDate;
    private Button btnSaveTrip;
    private ProgressBar progressBar;
    
    private TripsViewModel viewModel;
    private Calendar startCalendar;
    private Calendar endCalendar;
    private SimpleDateFormat dateFormat;
    
    private boolean isEditMode = false;
    private Trip currentTrip = null;
    private int tripId = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_trip);
        
        initViews();
        setupViewModel();
        setupDatePickers();
        setupClickListeners();
        setupToolbar();
        
        // Check if we are in edit mode
        if (getIntent().hasExtra("trip_id")) {
            tripId = getIntent().getIntExtra("trip_id", -1);
            if (tripId != -1) {
                isEditMode = true;
                loadTripData();
            }
        }
        
        // Test database connectivity
        testDatabaseConnection();
    }
    
    private void initViews() {
        editTripTitle = findViewById(R.id.edit_trip_title);
        editDestination = findViewById(R.id.edit_destination);
        textStartDate = findViewById(R.id.text_start_date);
        textEndDate = findViewById(R.id.text_end_date);
        btnStartDate = findViewById(R.id.btn_start_date);
        btnEndDate = findViewById(R.id.btn_end_date);
        btnSaveTrip = findViewById(R.id.btn_save_trip);
        progressBar = findViewById(R.id.progress_bar);
    }
    
    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);
    }
    
    private void setupDatePickers() {
        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();
        endCalendar.add(Calendar.DAY_OF_MONTH, 7); // Default to 1 week trip
        
        dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        
        // Set initial dates
        updateDateDisplays();
    }
    
    private void setupClickListeners() {
        btnStartDate.setOnClickListener(v -> showStartDatePicker());
        btnEndDate.setOnClickListener(v -> showEndDatePicker());
        btnSaveTrip.setOnClickListener(v -> saveTrip());
    }
    
    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (isEditMode) {
                getSupportActionBar().setTitle(R.string.edit_trip);
            } else {
                getSupportActionBar().setTitle("Add New Trip");
            }
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
    
    private void showStartDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                startCalendar.set(year, month, dayOfMonth);
                
                // If start date is after end date, update end date
                if (startCalendar.after(endCalendar)) {
                    endCalendar.setTime(startCalendar.getTime());
                    endCalendar.add(Calendar.DAY_OF_MONTH, 1);
                }
                
                updateDateDisplays();
            },
            startCalendar.get(Calendar.YEAR),
            startCalendar.get(Calendar.MONTH),
            startCalendar.get(Calendar.DAY_OF_MONTH)
        );
        
        // Don't allow past dates
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }
    
    private void showEndDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                endCalendar.set(year, month, dayOfMonth);
                updateDateDisplays();
            },
            endCalendar.get(Calendar.YEAR),
            endCalendar.get(Calendar.MONTH),
            endCalendar.get(Calendar.DAY_OF_MONTH)
        );
        
        // Don't allow dates before start date
        datePickerDialog.getDatePicker().setMinDate(startCalendar.getTimeInMillis());
        datePickerDialog.show();
    }
    
    private void updateDateDisplays() {
        textStartDate.setText(dateFormat.format(startCalendar.getTime()));
        textEndDate.setText(dateFormat.format(endCalendar.getTime()));
    }
    
    private void loadTripData() {
        viewModel.getTripById(tripId).observe(this, trip -> {
            if (trip != null) {
                currentTrip = trip;
                populateUiWithTripData();
            }
        });
    }

    private void populateUiWithTripData() {
        if (currentTrip == null) return;

        editTripTitle.setText(currentTrip.getTitle());
        editDestination.setText(currentTrip.getDestination());

        startCalendar.setTimeInMillis(currentTrip.getStartDate());
        endCalendar.setTimeInMillis(currentTrip.getEndDate());
        updateDateDisplays();

        // Update toolbar title again as this might be called after setupToolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.edit_trip);
        }
    }
    
    private void saveTrip() {
        String title = editTripTitle.getText().toString().trim();
        String destination = editDestination.getText().toString().trim();
        
        // Validate inputs
        if (TextUtils.isEmpty(title)) {
            editTripTitle.setError("Trip title is required");
            editTripTitle.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(destination)) {
            editDestination.setError("Destination is required");
            editDestination.requestFocus();
            return;
        }
        
        if (endCalendar.before(startCalendar)) {
            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading
        setLoadingState(true);
        
        if (isEditMode) {
            updateCurrentTrip(title, destination);
        } else {
            insertNewTrip(title, destination);
        }
    }

    private void insertNewTrip(String title, String destination) {
        try {
            // Create trip object with validation
            Trip trip = new Trip(title, destination, startCalendar.getTimeInMillis(), endCalendar.getTimeInMillis());
        
            // Log trip details for debugging
            Log.d(TAG, "Saving trip: " + title + ", " + destination + ", " + 
                  startCalendar.getTimeInMillis() + " to " + endCalendar.getTimeInMillis());
            
            // Add safeguards for ViewModel and repository
            if (viewModel == null) {
                Log.e(TAG, "ViewModel is null - reinitializing");
                setupViewModel();
                if (viewModel == null) {
                    setLoadingState(false);
                    Toast.makeText(this, "App error: Unable to initialize data handler", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            
            // Save trip with enhanced error handling
            viewModel.insertTrip(trip, new TripRepository.OnTripOperationListener() {
                @Override
                public void onSuccess(int tripId) {
                    // Ensure we're on UI thread
                    if (AddTripActivity.this.isDestroyed() || AddTripActivity.this.isFinishing()) {
                        Log.w(TAG, "Activity is destroyed/finishing, ignoring success callback");
                        return;
                    }
                    
                    runOnUiThread(() -> {
                        try {
                            setLoadingState(false);
                            Log.d(TAG, "Trip saved successfully with ID: " + tripId);
                            
                            // Show success animation and sound
                            SuccessDialogHelper.showSuccessDialog(
                                AddTripActivity.this, 
                                getString(R.string.trip_created_successfully),
                                () -> {
                                    // Callback when dialog is dismissed
                                    if (!AddTripActivity.this.isDestroyed() && !AddTripActivity.this.isFinishing()) {
                                        finish();
                                    }
                                }
                            );
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error in success callback UI updates", e);
                            // Still try to finish the activity
                            if (!AddTripActivity.this.isDestroyed() && !AddTripActivity.this.isFinishing()) {
                                finish();
                            }
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    // Ensure we're on UI thread
                    if (AddTripActivity.this.isDestroyed() || AddTripActivity.this.isFinishing()) {
                        Log.w(TAG, "Activity is destroyed/finishing, ignoring error callback");
                        return;
                    }
                    
                    runOnUiThread(() -> {
                        try {
                            setLoadingState(false);
                            Log.e(TAG, "Detailed error saving trip: " + error);
                            
                            // Show user-friendly error message
                            String userMessage = "Failed to save trip";
                            if (error != null) {
                                if (error.contains("network") || error.contains("connection")) {
                                    userMessage = "Network error - trip saved locally";
                                } else if (error.contains("Firebase") || error.contains("sync")) {
                                    userMessage = "Sync error - trip saved locally";
                                } else {
                                    userMessage = "Error: " + error;
                                }
                            }
                            
                            Toast.makeText(AddTripActivity.this, userMessage, Toast.LENGTH_LONG).show();
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error in error callback UI updates", e);
                        }
                    });
                }
            });
            
        } catch (IllegalArgumentException e) {
            setLoadingState(false);
            Log.e(TAG, "Validation error: " + e.getMessage());
            Toast.makeText(this, "Validation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            setLoadingState(false);
            Log.e(TAG, "Unexpected error creating trip: " + e.getMessage(), e);
            Toast.makeText(this, "Unexpected error occurred. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateCurrentTrip(String title, String destination) {
        if (currentTrip == null) {
            setLoadingState(false);
            Toast.makeText(this, "Error: Trip data not loaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentTrip.setTitle(title);
        currentTrip.setDestination(destination);
        currentTrip.setStartDate(startCalendar.getTimeInMillis());
        currentTrip.setEndDate(endCalendar.getTimeInMillis());
        currentTrip.setUpdatedAt(System.currentTimeMillis());

        viewModel.updateTrip(currentTrip, new TripRepository.OnTripOperationListener() {
            @Override
            public void onSuccess(int tripId) {
                runOnUiThread(() -> {
                    setLoadingState(false);
                    SuccessDialogHelper.showSuccessDialog(
                        AddTripActivity.this,
                        getString(R.string.trip_updated_successfully),
                        () -> finish()
                    );
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoadingState(false);
                    Log.e(TAG, "Error updating trip: " + error);
                    Toast.makeText(AddTripActivity.this, "Failed to update trip: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void setLoadingState(boolean loading) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            }
            if (btnSaveTrip != null) {
                btnSaveTrip.setEnabled(!loading);
            }
            if (btnStartDate != null) {
                btnStartDate.setEnabled(!loading);
            }
            if (btnEndDate != null) {
                btnEndDate.setEnabled(!loading);
            }
            if (editTripTitle != null) {
                editTripTitle.setEnabled(!loading);
            }
            if (editDestination != null) {
                editDestination.setEnabled(!loading);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating loading state", e);
        }
    }
    
    private void testDatabaseConnection() {
        try {
            Log.d(TAG, "Testing database connection...");
            // This will initialize the database and repository
            if (viewModel != null) {
                Log.d(TAG, "ViewModel initialized successfully");
            } else {
                Log.e(TAG, "Failed to initialize ViewModel");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error testing database connection", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        try {
            SuccessDialogHelper.releaseMediaPlayer();
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
} 