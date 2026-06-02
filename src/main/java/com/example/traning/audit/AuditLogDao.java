package com.example.traning.audit;

import java.time.LocalDate;
import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface AuditLogDao {

    @Insert
    int insert(AuditLogEntry entry);

    @Select
    List<AuditLogEntry> selectForAdmin(
            Long userId,
            String action,
            LocalDate from,
            LocalDate to,
            int offset,
            int limit);

    @Select
    int countForAdmin(
            Long userId,
            String action,
            LocalDate from,
            LocalDate to);
}
