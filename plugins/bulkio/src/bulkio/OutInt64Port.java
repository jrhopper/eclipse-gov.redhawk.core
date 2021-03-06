/*
 * This file is protected by Copyright. Please refer to the COPYRIGHT file
 * distributed with this source distribution.
 *
 * This file is part of REDHAWK bulkioInterfaces.
 *
 * REDHAWK bulkioInterfaces is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * REDHAWK bulkioInterfaces is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
/*
 * WARNING: This file is generated from OutPort.java.template.
 *          Do not modify directly.
 */
package bulkio;

import java.util.Arrays;

import org.apache.log4j.Logger;

import BULKIO.PrecisionUTCTime;
import BULKIO.dataLongLongOperations;

/**
 * BulkIO output port implementation for dataLongLong.
 */
public class OutInt64Port extends OutStreamPort<dataLongLongOperations,long[]> {

    public OutInt64Port(String portName) {
        this(portName, null, null);
    }

    public OutInt64Port(String portName, Logger logger) {
        this(portName, logger, null);
    }

    public OutInt64Port(String portName, Logger logger, ConnectionEventListener eventCB) {
        super(portName, logger, eventCB, 8);
        if (this.logger != null) {
            this.logger.debug("bulkio.OutPort CTOR port: " + portName);
        }

    }

    protected dataLongLongOperations narrow(final org.omg.CORBA.Object obj) {
        return BULKIO.jni.dataLongLongHelper.narrow(obj);
    }

    protected void sendPacket(dataLongLongOperations port, long[] data, PrecisionUTCTime time,
                              boolean endOfStream, String streamID) {
        port.pushPacket(data, time, endOfStream, streamID);
    }

    protected long[] copyOfRange(long[] array, int start, int end) {
        return Arrays.copyOfRange(array, start, end);
    }

    protected int arraySize(long[] array) {
        return array.length;
    }

    protected long[] emptyArray() {
        return new long[0];
    }

    public String getRepid() {
        return BULKIO.dataLongLongHelper.id();
    }
}

