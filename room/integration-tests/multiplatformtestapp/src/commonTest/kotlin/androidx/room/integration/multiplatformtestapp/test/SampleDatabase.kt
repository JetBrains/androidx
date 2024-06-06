/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.integration.multiplatformtestapp.test

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.MapColumn
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity
data class SampleEntity(
    @PrimaryKey val pk: Long,
    @ColumnInfo(defaultValue = "0") val data: Long = 0
)

@Entity
data class SampleEntity2(
    @PrimaryKey val pk2: Long,
    @ColumnInfo(defaultValue = "0") val data2: Long
)

@Entity(
    foreignKeys =
        [ForeignKey(entity = SampleEntity2::class, parentColumns = ["pk2"], childColumns = ["pk3"])]
)
data class SampleEntity3(
    @PrimaryKey val pk3: Long,
    @ColumnInfo(defaultValue = "0") val data3: Long
)

@Entity
data class SampleEntityCopy(
    @PrimaryKey val pk: Long,
    @ColumnInfo(defaultValue = "0") val dataCopy: Long
)

@Entity(
    primaryKeys = ["sample1Key", "sample2Key"],
    indices = [Index("sample1Key"), Index("sample2Key")]
)
data class Sample1Sample2XRef(
    val sample1Key: Long,
    val sample2Key: Long,
)

@Dao
interface SampleDao {

    @Query("INSERT INTO SampleEntity (pk) VALUES (:pk)") suspend fun insertItem(pk: Long): Long

    @Query("DELETE FROM SampleEntity WHERE pk = :pk") suspend fun deleteItem(pk: Long): Int

    @Query("SELECT * FROM SampleEntity") suspend fun getSingleItem(): SampleEntity

    @Query("SELECT * FROM SampleEntity") suspend fun getItemList(): List<SampleEntity>

    @Query("SELECT * FROM SampleEntity") suspend fun getItemArray(): Array<SampleEntity>

    @Query("SELECT * FROM SampleEntity") fun getItemListFlow(): Flow<List<SampleEntity>>

    @Transaction
    suspend fun deleteList(pks: List<Long>, withError: Boolean = false) {
        require(!withError)
        pks.forEach { deleteItem(it) }
    }

    @Transaction
    suspend fun deleteArray(entities: Array<SampleEntity>, withError: Boolean = false) {
        require(!withError)
        entities.forEach { delete(it) }
    }

    @Query("SELECT * FROM SampleEntity") suspend fun getSingleItemWithColumn(): SampleEntity

    @Query("SELECT * FROM SampleEntity JOIN SampleEntity2 ON SampleEntity.pk = SampleEntity2.pk2")
    suspend fun getSimpleMapReturnType(): Map<SampleEntity, SampleEntity2>

    @Query(
        "SELECT * FROM SampleEntity JOIN SampleEntityCopy ON SampleEntity.pk = SampleEntityCopy.pk"
    )
    suspend fun getMapWithDupeColumns(): Map<SampleEntity, SampleEntityCopy>

    @Query("SELECT * FROM SampleEntity JOIN SampleEntity2 ON SampleEntity.pk = SampleEntity2.pk2")
    suspend fun getMapReturnTypeWithList(): Map<SampleEntity, List<SampleEntity2>>

    @Query("SELECT * FROM SampleEntity JOIN SampleEntity2 ON SampleEntity.pk = SampleEntity2.pk2")
    suspend fun getMapReturnTypeWithSet(): Map<SampleEntity, Set<SampleEntity2>>

    @Query(
        """
        SELECT * FROM SampleEntity
        JOIN SampleEntity2 ON (SampleEntity.pk = SampleEntity2.pk2)
        JOIN SampleEntity3 ON (SampleEntity2.pk2 = SampleEntity3.pk3)
        """
    )
    suspend fun getSimpleNestedMapReturnType(): Map<SampleEntity, Map<SampleEntity2, SampleEntity3>>

    @Query(
        """
        SELECT * FROM SampleEntity
        JOIN SampleEntity2 ON (SampleEntity.pk = SampleEntity2.pk2)
        JOIN SampleEntity3 ON (SampleEntity2.pk2 = SampleEntity3.pk3)
        """
    )
    @RewriteQueriesToDropUnusedColumns
    suspend fun getSimpleNestedMapColumnMap():
        Map<SampleEntity, Map<SampleEntity2, @MapColumn(columnName = "data3") Long>>

    @Insert suspend fun insert(entity: SampleEntity)

    @Insert suspend fun insertArray(entities: Array<SampleEntity>)

    @Insert suspend fun insertSampleEntityList(entities: List<SampleEntity>)

    @Insert suspend fun insertSampleEntity2List(entities: List<SampleEntity2>)

    @Insert suspend fun insert(entity: SampleEntity2)

    @Insert suspend fun insert(entity: SampleEntity3)

    @Insert suspend fun insert(entity: SampleEntityCopy)

    @Upsert suspend fun upsert(entity: SampleEntity)

    @Delete suspend fun delete(entity: SampleEntity)

    @Update suspend fun update(entity: SampleEntity)

    @Query("SELECT * FROM SampleEntity") suspend fun queryOfArray(): Array<SampleEntity>

    @Query("SELECT pk FROM SampleEntity") suspend fun queryOfArrayWithLong(): Array<Long>

    @Query("SELECT pk FROM SampleEntity") suspend fun queryOfLongArray(): LongArray

    @Transaction @Query("SELECT * FROM SampleEntity") suspend fun getSample1To2(): Sample1And2

    @Transaction @Query("SELECT * FROM SampleEntity") suspend fun getSample1ToMany(): Sample1AndMany

    @Transaction
    @Query("SELECT * FROM SampleEntity")
    suspend fun getSampleManyToMany(): SampleManyAndMany

    data class Sample1And2(
        @Embedded val sample1: SampleEntity,
        @Relation(parentColumn = "pk", entityColumn = "pk2") val sample2: SampleEntity2
    )

    data class Sample1AndMany(
        @Embedded val sample1: SampleEntity,
        @Relation(parentColumn = "pk", entityColumn = "pk2") val sample2s: List<SampleEntity2>
    )

    data class SampleManyAndMany(
        @Embedded val sample1: SampleEntity,
        @Relation(
            parentColumn = "pk",
            entityColumn = "pk2",
            associateBy =
                Junction(
                    value = Sample1Sample2XRef::class,
                    parentColumn = "sample1Key",
                    entityColumn = "sample2Key"
                )
        )
        val sample2s: List<SampleEntity2>
    )
}

@Database(
    entities =
        [
            SampleEntity::class,
            SampleEntity2::class,
            SampleEntity3::class,
            SampleEntityCopy::class,
            Sample1Sample2XRef::class
        ],
    version = 1,
    exportSchema = false
)
abstract class SampleDatabase : RoomDatabase() {
    abstract fun dao(): SampleDao
}
