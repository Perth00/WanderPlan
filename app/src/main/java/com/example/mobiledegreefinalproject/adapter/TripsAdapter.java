package com.example.mobiledegreefinalproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobiledegreefinalproject.R;
import com.example.mobiledegreefinalproject.database.Trip;

public class TripsAdapter extends ListAdapter<Trip, TripsAdapter.TripViewHolder> {
    
    public interface OnTripClickListener {
        void onTripClick(Trip trip);
    }
    
    private final OnTripClickListener clickListener;
    
    public TripsAdapter(OnTripClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }
    
    private static final DiffUtil.ItemCallback<Trip> DIFF_CALLBACK = new DiffUtil.ItemCallback<Trip>() {
        @Override
        public boolean areItemsTheSame(@NonNull Trip oldItem, @NonNull Trip newItem) {
            return oldItem.getId() == newItem.getId();
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull Trip oldItem, @NonNull Trip newItem) {
            return oldItem.getTitle().equals(newItem.getTitle()) &&
                   oldItem.getDestination().equals(newItem.getDestination()) &&
                   oldItem.getStartDate() == newItem.getStartDate() &&
                   oldItem.getEndDate() == newItem.getEndDate();
        }
    };
    
    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = getItem(position);
        holder.bind(trip, clickListener);
    }
    
    static class TripViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView destinationText;
        private final TextView dateRangeText;
        private final TextView durationText;
        private final ImageView mapPreview;
        
        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.trip_title);
            destinationText = itemView.findViewById(R.id.trip_destination);
            dateRangeText = itemView.findViewById(R.id.trip_date_range);
            durationText = itemView.findViewById(R.id.trip_duration);
            mapPreview = itemView.findViewById(R.id.trip_map_preview);
        }
        
        public void bind(Trip trip, OnTripClickListener clickListener) {
            titleText.setText(trip.getTitle());
            destinationText.setText(trip.getDestination());
            dateRangeText.setText(trip.getDateRange());
            
            int days = trip.getDurationDays();
            durationText.setText(days + (days == 1 ? " day" : " days"));
            
            // Set click listener
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTripClick(trip);
                }
            });
            
            // TODO: Load map preview image using Glide when map URLs are available
            // For now, show a placeholder
            mapPreview.setImageResource(R.drawable.ic_trips);
        }
    }
} 