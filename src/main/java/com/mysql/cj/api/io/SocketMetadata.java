/*
  Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.

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

package com.mysql.cj.api.io;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import com.mysql.cj.api.Session;
import com.mysql.cj.core.Messages;

public interface SocketMetadata {

    default boolean isLocallyConnected(Session sess) {
        String processHost = sess.getProcessHost();
        return isLocallyConnected(sess, processHost);
    }

    default boolean isLocallyConnected(Session sess, String processHost) {
        if (processHost != null) {
            sess.getLog().logDebug(Messages.getString("SocketMetadata.0", new Object[] { processHost }));

            int endIndex = processHost.lastIndexOf(":");
            if (endIndex != -1) {
                processHost = processHost.substring(0, endIndex);

                try {

                    InetAddress[] whereMysqlThinksIConnectedFrom = InetAddress.getAllByName(processHost);

                    SocketAddress remoteSocketAddr = sess.getRemoteSocketAddress();

                    if (remoteSocketAddr instanceof InetSocketAddress) {
                        InetAddress whereIConnectedTo = ((InetSocketAddress) remoteSocketAddr).getAddress();

                        for (InetAddress hostAddr : whereMysqlThinksIConnectedFrom) {
                            if (hostAddr.equals(whereIConnectedTo)) {
                                sess.getLog().logDebug(Messages.getString("SocketMetadata.1", new Object[] { hostAddr, whereIConnectedTo }));
                                return true;
                            }
                            sess.getLog().logDebug(Messages.getString("SocketMetadata.2", new Object[] { hostAddr, whereIConnectedTo }));
                        }

                    } else {
                        sess.getLog().logDebug(Messages.getString("SocketMetadata.3", new Object[] { remoteSocketAddr }));
                    }

                    return false;
                } catch (UnknownHostException e) {
                    sess.getLog().logWarn(Messages.getString("Connection.CantDetectLocalConnect", new Object[] { processHost }), e);

                    return false;
                }

            }
            sess.getLog().logWarn(Messages.getString("SocketMetadata.4", new Object[] { processHost }));
            return false;
        }

        return false;
    }

}