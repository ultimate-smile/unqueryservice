package com.unqueryservice.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CalciteQueryServiceTest {

    @Test
    void timestampColumnsUseJdbcTimestampGetterInsteadOfVendorObject() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        Timestamp timestamp = Timestamp.valueOf("2026-06-16 12:34:56");
        when(meta.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(rs.getTimestamp(1)).thenReturn(timestamp);

        Object value = CalciteQueryService.toJsonSafeValue(rs, meta, 1);

        assertThat(value).isEqualTo(LocalDateTime.of(2026, 6, 16, 12, 34, 56));
        verify(rs, never()).getObject(1);
    }

    @Test
    void unsupportedJdbcObjectsFallBackToStringValues() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        ByteArrayInputStream vendorObject = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(meta.getColumnType(1)).thenReturn(Types.OTHER);
        when(rs.getObject(1)).thenReturn(vendorObject);

        Object value = CalciteQueryService.toJsonSafeValue(rs, meta, 1);

        assertThat(value).isEqualTo(vendorObject.toString());
    }
}
