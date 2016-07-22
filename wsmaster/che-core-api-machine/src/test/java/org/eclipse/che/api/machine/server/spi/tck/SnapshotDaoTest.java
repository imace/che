/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.machine.server.spi.tck;

import com.google.inject.Inject;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.util.SystemInfo;
import org.eclipse.che.api.machine.server.model.impl.MachineSourceImpl;
import org.eclipse.che.api.machine.server.model.impl.SnapshotImpl;
import org.eclipse.che.api.machine.server.spi.SnapshotDao;
import org.eclipse.che.commons.test.tck.TckModuleFactory;
import org.eclipse.che.commons.test.tck.repository.TckRepository;
import org.eclipse.che.commons.test.tck.repository.TckRepositoryException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

/**
 * Tests {@link SnapshotDao} contract.
 *
 * @author Yevhenii Voevodin
 */
@Guice(moduleFactory = TckModuleFactory.class)
@Test(suiteName = SnapshotDaoTest.SUITE_NAME)
public class SnapshotDaoTest {

    public static final String SUITE_NAME = "SnapshotDaoTest";

    private static final int SNAPSHOTS_SIZE = 6;

    private SnapshotImpl[] snapshots;

    @Inject
    private SnapshotDao snapshotDao;

    @Inject
    private TckRepository<SnapshotImpl> snaphotRepo;

    @BeforeMethod
    private void createSnapshots() throws TckRepositoryException {
        snapshots = new SnapshotImpl[SNAPSHOTS_SIZE];
        for (int i = 0; i < SNAPSHOTS_SIZE; i++) {
            snapshots[i] = createSnapshot("snapshot-" + i,
                                          "workspace-" + i / 3, // 3 snapshot share the same workspace id
                                          "environment-" + i / 2, // 2 snapshots share the same env name
                                          "machine-" + i);
        }
        snaphotRepo.createAll(asList(snapshots));
    }

    @AfterMethod
    private void removeSnapshots() throws TckRepositoryException {
        snaphotRepo.removeAll();
    }

    @Test
    public void shouldGetSnapshotById() throws Exception {
        final SnapshotImpl snapshot = snapshots[0];

        assertEquals(snapshotDao.getSnapshot(snapshot.getId()), snapshot);
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldThrowNotFoundExceptionWhenGettingNonExistingSnapshot() throws Exception {
        snapshotDao.getSnapshot("non-existing-snapshot");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldThrowNpeWhenGettingSnapshotByNullId() throws Exception {
        snapshotDao.getSnapshot(null);
    }

    @Test
    public void shouldGetSnapshotByWorkspaceEnvironmentAndMachineName() throws Exception {
        final SnapshotImpl snapshot = snapshots[0];

        assertEquals(snapshotDao.getSnapshot(snapshot.getWorkspaceId(),
                                             snapshot.getEnvName(),
                                             snapshot.getMachineName()), snapshot);
    }

    private static SnapshotImpl createSnapshot(String id, String workspaceId, String envName, String machineName) {
        return SnapshotImpl.builder()
                           .setId(id)
                           .setType(id + "type")
                           .setMachineSource(new MachineSourceImpl(id + "source-type",
                                                                   id + "source-location",
                                                                   id + "source-content"))
                           .setNamespace(id + "namespace")
                           .setCreationDate(System.currentTimeMillis())
                           .setDev(true)
                           .setWorkspaceId(workspaceId)
                           .setEnvName(envName)
                           .setMachineName(machineName)
                           .build();
    }
}
