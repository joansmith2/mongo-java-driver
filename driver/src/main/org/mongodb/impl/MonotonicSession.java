/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.impl;

import org.mongodb.AbstractBaseSession;
import org.mongodb.Cluster;
import org.mongodb.ReadPreference;
import org.mongodb.Session;
import org.mongodb.annotations.NotThreadSafe;

import static org.mongodb.assertions.Assertions.isTrue;
import static org.mongodb.assertions.Assertions.notNull;

@NotThreadSafe
public class MonotonicSession extends AbstractBaseSession implements Session {
    private ReadPreference lastRequestedReadPreference;
    private Connection connectionForReads;
    private Connection connectionForWrites;

    public MonotonicSession(final Cluster cluster) {
        super(cluster);
    }

    @Override
    public Connection getConnection(final ReadPreference readPreference) {
        isTrue("open", !isClosed());
        notNull("readPreference", readPreference);
        synchronized (this) {
            Connection connectionToUse;
            if (connectionForWrites != null) {
                connectionToUse = connectionForWrites;
            }
            else if (connectionForReads == null || !readPreference.equals(lastRequestedReadPreference)) {
                lastRequestedReadPreference = readPreference;
                if (connectionForReads != null) {
                    connectionForReads.close();
                }
                connectionForReads = getCluster().getServer(readPreference).getConnection();

                connectionToUse = connectionForReads;
            }
            else {
                connectionToUse = connectionForReads;
            }
            return new DelayedCloseConnection(connectionToUse);
        }
    }

    @Override
    public Connection getConnection() {
        isTrue("open", !isClosed());
        synchronized (this) {
            if (connectionForWrites == null) {
                connectionForWrites = getCluster().getServer(ReadPreference.primary()).getConnection();
                if (connectionForReads != null) {
                    connectionForReads.close();
                    connectionForReads = null;
                }
            }
            return new DelayedCloseConnection(connectionForWrites);
        }
    }

    @Override
    public void close() {
        if (!isClosed()) {
            if (connectionForReads != null) {
                connectionForReads.close();
                connectionForReads = null;
            }
            if (connectionForWrites != null) {
                connectionForWrites.close();
                connectionForWrites = null;
            }
        }
    }
}