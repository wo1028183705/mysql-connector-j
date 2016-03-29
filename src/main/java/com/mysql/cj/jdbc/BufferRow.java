/*
  Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package com.mysql.cj.jdbc;

import java.sql.SQLException;

import com.mysql.cj.api.exceptions.ExceptionInterceptor;
import com.mysql.cj.api.io.ValueDecoder;
import com.mysql.cj.api.io.ValueFactory;
import com.mysql.cj.core.Messages;
import com.mysql.cj.core.result.Field;
import com.mysql.cj.jdbc.exceptions.OperationNotSupportedException;
import com.mysql.cj.jdbc.exceptions.SQLError;
import com.mysql.cj.mysqla.MysqlaConstants;
import com.mysql.cj.mysqla.MysqlaUtils;
import com.mysql.cj.mysqla.io.Buffer;

/**
 * A ResultSetRow implementation that holds one row packet (which is re-used by the driver, and thus saves memory allocations), and tries when possible to avoid
 * allocations to break out the results as individual byte[]s.
 * 
 * (this isn't possible when doing things like reading floating point values).
 */
public class BufferRow extends ResultSetRow {
    private Buffer rowFromServer;

    /**
     * The beginning of the row packet
     */
    private int homePosition = 0;

    /**
     * The home position before the is-null bitmask for server-side prepared statement result sets
     */
    private int preNullBitmaskHomePosition = 0;

    /**
     * The last-requested index, used as an optimization, if you ask for the same index, we won't seek to find it. If you ask for an index that is >
     * than the last one requested, we start seeking from the last requested index.
     */
    private int lastRequestedIndex = -1;

    /**
     * The position of the last-requested index, optimization in concert with lastRequestedIndex.
     */
    private int lastRequestedPos;

    /**
     * The metadata of the fields of this result set.
     */
    private Field[] metadata;

    /**
     * Is this a row from a server-side prepared statement? If so, they're encoded differently, so we have different ways of finding where each column is, and
     * unpacking them.
     */
    private boolean isBinaryEncoded;

    /**
     * If binary-encoded, the NULL status of each column is at the beginning of the row, so we
     */
    private boolean[] isNull;

    public BufferRow(Buffer buf, Field[] fields, boolean isBinaryEncoded, ExceptionInterceptor exceptionInterceptor, ValueDecoder valueDecoder)
            throws SQLException {
        super(exceptionInterceptor);

        this.rowFromServer = buf;
        this.metadata = fields;
        this.isBinaryEncoded = isBinaryEncoded;
        this.homePosition = this.rowFromServer.getPosition();
        this.preNullBitmaskHomePosition = this.homePosition;
        this.valueDecoder = valueDecoder;

        if (fields != null) {
            setMetadata(fields);
        }
    }

    private int findAndSeekToOffset(int index) throws SQLException {
        if (!this.isBinaryEncoded) {

            if (index == 0) {
                this.lastRequestedIndex = 0;
                this.lastRequestedPos = this.homePosition;
                this.rowFromServer.setPosition(this.homePosition);

                return 0;
            }

            if (index == this.lastRequestedIndex) {
                this.rowFromServer.setPosition(this.lastRequestedPos);

                return this.lastRequestedPos;
            }

            int startingIndex = 0;

            if (index > this.lastRequestedIndex) {
                if (this.lastRequestedIndex >= 0) {
                    startingIndex = this.lastRequestedIndex;
                } else {
                    startingIndex = 0;
                }

                this.rowFromServer.setPosition(this.lastRequestedPos);
            } else {
                this.rowFromServer.setPosition(this.homePosition);
            }

            for (int i = startingIndex; i < index; i++) {
                this.rowFromServer.fastSkipLenByteArray();
            }

            this.lastRequestedIndex = index;
            this.lastRequestedPos = this.rowFromServer.getPosition();

            return this.lastRequestedPos;
        }

        return findAndSeekToOffsetForBinaryEncoding(index);
    }

    private int findAndSeekToOffsetForBinaryEncoding(int index) throws SQLException {
        if (index == 0) {
            this.lastRequestedIndex = 0;
            this.lastRequestedPos = this.homePosition;
            this.rowFromServer.setPosition(this.homePosition);

            return 0;
        }

        if (index == this.lastRequestedIndex) {
            this.rowFromServer.setPosition(this.lastRequestedPos);

            return this.lastRequestedPos;
        }

        int startingIndex = 0;

        if (index > this.lastRequestedIndex) {
            if (this.lastRequestedIndex >= 0) {
                startingIndex = this.lastRequestedIndex;
            } else {
                // First-time "scan"
                startingIndex = 0;
                this.lastRequestedPos = this.homePosition;
            }

            this.rowFromServer.setPosition(this.lastRequestedPos);
        } else {
            this.rowFromServer.setPosition(this.homePosition);
        }

        for (int i = startingIndex; i < index; i++) {
            if (this.isNull[i]) {
                continue;
            }

            int type = this.metadata[i].getMysqlTypeId();

            if (type != MysqlaConstants.FIELD_TYPE_NULL) {
                int length = MysqlaUtils.getBinaryEncodedLength(this.metadata[i].getMysqlTypeId());
                if (length == 0) {
                    this.rowFromServer.fastSkipLenByteArray();
                } else if (length == -1) {
                    throw SQLError
                            .createSQLException(
                                    Messages.getString("MysqlIO.97") + type + Messages.getString("MysqlIO.98") + (i + 1) + Messages.getString("MysqlIO.99")
                                            + this.metadata.length + Messages.getString("MysqlIO.100"),
                                    SQLError.SQL_STATE_GENERAL_ERROR, this.exceptionInterceptor);
                } else {
                    int curPosition = this.rowFromServer.getPosition();
                    this.rowFromServer.setPosition(curPosition + length);
                }
            }
        }

        this.lastRequestedIndex = index;
        this.lastRequestedPos = this.rowFromServer.getPosition();

        return this.lastRequestedPos;
    }

    @Override
    public byte[] getColumnValue(int index) throws SQLException {
        findAndSeekToOffset(index);

        if (!this.isBinaryEncoded) {
            return this.rowFromServer.readLenByteArray(0);
        }

        if (this.getNull(index)) {
            return null;
        }

        int type = this.metadata[index].getMysqlTypeId();

        switch (type) {
            case MysqlaConstants.FIELD_TYPE_NULL:
                return null;

            case MysqlaConstants.FIELD_TYPE_TINY:
                return new byte[] { this.rowFromServer.readByte() };

            default:
                int length = MysqlaUtils.getBinaryEncodedLength(type);
                if (length == 0) {
                    return this.rowFromServer.readLenByteArray(0);
                } else if (length == -1) {
                    throw SQLError
                            .createSQLException(
                                    Messages.getString("MysqlIO.97") + type + Messages.getString("MysqlIO.98") + (index + 1) + Messages.getString("MysqlIO.99")
                                            + this.metadata.length + Messages.getString("MysqlIO.100"),
                                    SQLError.SQL_STATE_GENERAL_ERROR, this.exceptionInterceptor);
                } else {
                    return this.rowFromServer.getBytes(length);
                }
        }
    }

    @Override
    public boolean isNull(int index) throws SQLException {
        if (!this.isBinaryEncoded) {
            findAndSeekToOffset(index);

            return this.rowFromServer.readFieldLength() == Buffer.NULL_LENGTH;
        }

        return this.isNull[index];
    }

    @Override
    public long length(int index) throws SQLException {
        findAndSeekToOffset(index);

        long length = this.rowFromServer.readFieldLength();

        if (length == Buffer.NULL_LENGTH) {
            return 0;
        }

        return length;
    }

    @Override
    public void setColumnValue(int index, byte[] value) throws SQLException {
        throw new OperationNotSupportedException();
    }

    @Override
    public ResultSetRow setMetadata(Field[] f) throws SQLException {
        super.setMetadata(f);

        if (this.isBinaryEncoded) {
            setupIsNullBitmask();
        }

        return this;
    }

    /**
     * Unpacks the bitmask at the head of the row packet that tells us what
     * columns hold null values, and sets the "home" position directly after the
     * bitmask.
     */
    private void setupIsNullBitmask() throws SQLException {
        if (this.isNull != null) {
            return; // we've already done this
        }

        this.rowFromServer.setPosition(this.preNullBitmaskHomePosition);

        int nullCount = (this.metadata.length + 9) / 8;

        byte[] nullBitMask = new byte[nullCount];

        for (int i = 0; i < nullCount; i++) {
            nullBitMask[i] = this.rowFromServer.readByte();
        }

        this.homePosition = this.rowFromServer.getPosition();

        this.isNull = new boolean[this.metadata.length];

        int nullMaskPos = 0;
        int bit = 4; // first two bits are reserved for future use

        for (int i = 0; i < this.metadata.length; i++) {

            this.isNull[i] = ((nullBitMask[nullMaskPos] & bit) != 0);

            if (((bit <<= 1) & 255) == 0) {
                bit = 1; /* To next byte */

                nullMaskPos++;
            }
        }
    }

    /**
     * Implementation of getValue() based on the underlying Buffer object. Delegate to superclass for decoding.
     */
    @Override
    public <T> T getValue(int columnIndex, ValueFactory<T> vf) throws SQLException {
        findAndSeekToOffset(columnIndex);

        int length;
        if (this.isBinaryEncoded) {
            // field length is type-specific in binary-encoded results
            int type = this.metadata[columnIndex].getMysqlTypeId();
            length = MysqlaUtils.getBinaryEncodedLength(type);
            if (length == 0) {
                length = (int) this.rowFromServer.readFieldLength();
            } else if (length == -1) {
                throw SQLError
                        .createSQLException(
                                Messages.getString("MysqlIO.97") + type + Messages.getString("MysqlIO.98") + (columnIndex + 1)
                                        + Messages.getString("MysqlIO.99") + this.metadata.length + Messages.getString("MysqlIO.100"),
                                SQLError.SQL_STATE_GENERAL_ERROR, this.exceptionInterceptor);
            }
        } else {
            length = (int) this.rowFromServer.readFieldLength();
        }
        // MUST get offset after we read the length, i.e. don't reverse order of these two statements
        int offset = this.rowFromServer.getPosition();

        return getValueFromBytes(columnIndex, this.rowFromServer.getByteBuffer(), offset, length, vf);
    }
}