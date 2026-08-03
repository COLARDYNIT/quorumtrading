package com.quorumtrading.adapter.persistence.converter;

import com.quorumtrading.model.enums.EventType;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLEnumUserType implements UserType<EventType> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<EventType> returnedClass() {
        return EventType.class;
    }

    @Override
    public boolean equals(EventType x, EventType y) throws HibernateException {
        if (x == y) {
            return true;
        }
        if (x == null || y == null) {
            return false;
        }
        return x.equals(y);
    }

    @Override
    public int hashCode(EventType x) throws HibernateException {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public EventType nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String value = rs.getString(position);
        if (value == null) {
            return null;
        }
        return EventType.valueOf(value);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, EventType value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value.name(), Types.OTHER);
        }
    }

    @Override
    public EventType deepCopy(EventType value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(EventType value) throws HibernateException {
        return value;
    }

    @Override
    public EventType assemble(Serializable cached, Object owner) throws HibernateException {
        return (EventType) cached;
    }

    @Override
    public EventType replace(EventType detached, EventType managed, Object owner) throws HibernateException {
        return detached;
    }
}
