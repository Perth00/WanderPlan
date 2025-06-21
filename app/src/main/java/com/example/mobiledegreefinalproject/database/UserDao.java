package com.example.mobiledegreefinalproject.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface UserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);
    
    @Update
    void updateUser(User user);
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    User getUserById(String userId);
    
    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    User getCurrentUser();
    
    @Query("SELECT * FROM users ORDER BY lastUpdated DESC")
    List<User> getAllUsers();
    
    @Query("UPDATE users SET isCurrentUser = 0")
    void clearCurrentUser();
    
    @Query("UPDATE users SET isCurrentUser = 1 WHERE userId = :userId")
    void setCurrentUser(String userId);
    
    @Query("DELETE FROM users WHERE userId = :userId")
    void deleteUser(String userId);
    
    @Query("DELETE FROM users")
    void deleteAllUsers();
    
    @Query("SELECT COUNT(*) FROM users")
    int getUserCount();
    
    @Query("SELECT * FROM users WHERE name LIKE :searchQuery OR email LIKE :searchQuery")
    List<User> searchUsers(String searchQuery);
} 