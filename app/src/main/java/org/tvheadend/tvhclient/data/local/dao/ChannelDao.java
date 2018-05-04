package org.tvheadend.tvhclient.data.local.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;
import android.arch.persistence.room.Update;

import org.tvheadend.tvhclient.data.entity.Channel;

import java.util.List;

@Dao
public interface ChannelDao {

    @Query("SELECT c.* FROM channels AS c " +
            "WHERE c.connection_id IN (SELECT id FROM connections WHERE active = 1) " +
            "ORDER BY CASE :sortOrder " +
            "   WHEN 0 THEN c.id " +
            "   WHEN 1 THEN c.name " +
            "   WHEN 2 THEN c.number " +
            "END ASC")
    List<Channel> loadAllChannelsSync(int sortOrder);

    @Query("SELECT c.* FROM channels AS c " +
            "WHERE c.connection_id IN (SELECT id FROM connections WHERE active = 1) " +
            " AND c.id = :id")
    Channel loadChannelByIdSync(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Channel> channels);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Channel channel);

    @Update
    void update(Channel... channels);

    @Update
    void update(List<Channel> channels);

    @Delete
    void delete(Channel channel);

    @Query("DELETE FROM channels")
    void deleteAll();

    @Query("DELETE FROM channels " +
            "WHERE connection_id IN (SELECT id FROM connections WHERE active = 1) " +
            " AND id = :id")
    void deleteById(int id);

    @Transaction
    @Query("SELECT c.*, " +
            "program.id AS program_id, " +
            "program.title AS program_title, " +
            "program.subtitle AS program_subtitle, " +
            "program.start AS program_start, " +
            "program.stop AS program_stop, " +
            "program.content_type AS program_content_type, " +
            "next_program.id AS next_program_id, " +
            "next_program.title AS next_program_title " +
            "FROM channels AS c " +
            "LEFT JOIN programs AS program ON program.start <= :time AND program.stop > :time AND program.channel_id = c.id " +
            "LEFT JOIN programs AS next_program ON next_program.id = program.id AND next_program.channel_id = c.id " +
            "WHERE c.connection_id IN (SELECT id FROM connections WHERE active = 1) " +
            " AND c.id IN (SELECT channel_id FROM tags_and_channels WHERE tag_id = :tagId) " +
            "GROUP BY c.id " +
            "ORDER BY CASE :sortOrder " +
            "   WHEN 0 THEN c.id " +
            "   WHEN 1 THEN c.name " +
            "   WHEN 2 THEN c.number " +
            "END ASC")
    List<Channel> loadAllChannelsByTimeAndTagSync(long time, int tagId, int sortOrder);

    @Transaction
    @Query("SELECT c.*, " +
            "program.id AS program_id, " +
            "program.title AS program_title, " +
            "program.subtitle AS program_subtitle, " +
            "program.start AS program_start, " +
            "program.stop AS program_stop, " +
            "program.content_type AS program_content_type, " +
            "next_program.id AS next_program_id, " +
            "next_program.title AS next_program_title " +
            "FROM channels AS c " +
            "LEFT JOIN programs AS program ON program.start <= :time AND program.stop > :time AND program.channel_id = c.id " +
            "LEFT JOIN programs AS next_program ON next_program.id = program.id AND next_program.channel_id = c.id " +
            "WHERE c.connection_id IN (SELECT id FROM connections WHERE active = 1) " +
            "GROUP BY c.id " +
            "ORDER BY CASE :sortOrder " +
            "   WHEN 0 THEN c.id " +
            "   WHEN 1 THEN c.name " +
            "   WHEN 2 THEN c.number " +
            "END ASC")
    List<Channel> loadAllChannelsByTimeSync(long time, int sortOrder);

    @Query("SELECT COUNT (*) FROM channels " +
            "WHERE connection_id IN (SELECT id FROM connections WHERE active = 1)")
    LiveData<Integer> getChannelCount();

    @Query("SELECT id FROM channels " +
            "WHERE connection_id IN (SELECT id FROM connections WHERE active = 1)")
    List<Integer> loadAllChannelIds();
}
