package com.lifo.mongo.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lifo.mongo.database.entity.ImageToDelete

@Dao
interface ImageToDeleteDao {

    @Query("SELECT * FROM image_to_delete_table ORDER BY id ASC")
    suspend fun getAllImages(): List<ImageToDelete>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addImageToDelete(imageToDelete: ImageToDelete)

    @Query("DELETE FROM image_to_delete_table WHERE id=:imageId")
    suspend fun cleanupImage(imageId: Int)

}