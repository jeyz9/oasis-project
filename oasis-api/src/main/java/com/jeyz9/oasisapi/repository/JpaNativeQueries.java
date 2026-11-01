package com.jeyz9.oasisapi.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared EntityManager support for PostgreSQL-specific locks, immutable history and report projections.
 * Domain repositories own SQL. Services own business decisions and DTO assembly.
 * Bulk writes flush then clear the persistence context to prevent stale entity reads.
 */
public abstract class JpaNativeQueries {
    @PersistenceContext protected EntityManager entityManager;

    private Query query(String sql,boolean projection,Object... parameters) {
        StringBuilder bound=new StringBuilder();boolean quoted=false;int index=0;
        for(int i=0;i<sql.length();i++) {
            char ch=sql.charAt(i);
            if(ch=='\'') {
                bound.append(ch);
                if(quoted && i+1<sql.length() && sql.charAt(i+1)=='\''){bound.append(sql.charAt(++i));continue;}
                quoted=!quoted;
            } else if(ch=='?' && !quoted) bound.append('?').append(++index);
            else bound.append(ch);
        }
        if(index!=parameters.length) throw new IllegalArgumentException("Invalid repository parameter count");
        entityManager.flush();
        Query query=projection?entityManager.createNativeQuery(bound.toString(),Tuple.class):entityManager.createNativeQuery(bound.toString());
        for(int i=0;i<parameters.length;i++) query.setParameter(i+1,parameters[i]);
        return query;
    }
    private Object normalize(Object value) {
        if(value instanceof OffsetDateTime time)return time.toInstant();
        if(value instanceof ZonedDateTime time)return time.toInstant();
        if(value instanceof java.util.Date date) {
            try{return date.toInstant();}catch(UnsupportedOperationException ignored){return LocalDate.parse(date.toString());}
        }
        return value;
    }
    protected List<Map<String,Object>> maps(String sql,Object... parameters) {
        List<?> results=query(sql,true,parameters).getResultList();var rows=new ArrayList<Map<String,Object>>();
        for(Object result:results) {
            Tuple tuple=(Tuple)result;var row=new LinkedHashMap<String,Object>();
            for(var column:tuple.getElements()) row.put(column.getAlias().toLowerCase(Locale.ROOT),normalize(tuple.get(column)));
            rows.add(row);
        }
        return rows;
    }
    protected Map<String,Object> map(String sql,Object... parameters) {
        var rows=maps(sql,parameters);if(rows.size()!=1)throw new IncorrectResultSizeDataAccessException(1,rows.size());return rows.getFirst();
    }
    protected <T> List<T> rows(String sql,ProjectionMapper<T> mapper,Object... parameters) {
        var values=maps(sql,parameters);var results=new ArrayList<T>();
        for(int i=0;i<values.size();i++)results.add(mapper.map(new ProjectionRow(values.get(i)),i));return results;
    }
    protected <T> T one(String sql,ProjectionMapper<T> mapper,Object... parameters) {
        var values=rows(sql,mapper,parameters);if(values.size()!=1)throw new IncorrectResultSizeDataAccessException(1,values.size());return values.getFirst();
    }
    private <T> T convert(Object value,Class<T> type) {
        if(value==null)return null;
        if(type==Long.class && value instanceof Number n)return type.cast(n.longValue());
        if(type==Integer.class && value instanceof Number n)return type.cast(n.intValue());
        return type.cast(value);
    }
    protected <T> List<T> scalars(String sql,Class<T> type,Object... parameters) {
        var results=new ArrayList<T>();for(var row:maps(sql,parameters))results.add(convert(row.values().iterator().next(),type));return results;
    }
    protected <T> T scalar(String sql,Class<T> type,Object... parameters) {
        var values=scalars(sql,type,parameters);if(values.size()!=1)throw new IncorrectResultSizeDataAccessException(1,values.size());return values.getFirst();
    }
    protected int execute(String sql,Object... parameters) {
        int changed=query(sql,false,parameters).executeUpdate();entityManager.clear();return changed;
    }
}
