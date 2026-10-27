// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.storage.dao;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.storage.Volume;
import com.cloud.storage.VolumeVO;
import com.cloud.utils.Pair;
import com.cloud.utils.db.Attribute;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;

@RunWith(MockitoJUnitRunner.class)
public class VolumeDaoImplTest {
    @Mock
    private PreparedStatement preparedStatementMock;

    @Mock
    private TransactionLegacy transactionMock;

    private static MockedStatic<TransactionLegacy> mockedTransactionLegacy;

    @Spy
    private final VolumeDaoImpl volumeDao = new VolumeDaoImpl();

    @BeforeClass
    public static void init() {
        mockedTransactionLegacy = Mockito.mockStatic(TransactionLegacy.class);
    }

    @AfterClass
    public static void close() {
        mockedTransactionLegacy.close();
    }

    @Test
    public void testListPoolIdsByVolumeCount_with_cluster_details() throws SQLException {
        final String ORDER_POOLS_NUMBER_OF_VOLUMES_FOR_ACCOUNT_QUERY_WITH_CLUSTER =
                "SELECT pool.id, SUM(IF(vol.state='Ready' AND vol.account_id = ?, 1, 0)) FROM `cloud`.`storage_pool` pool LEFT JOIN `cloud`.`volumes` vol ON pool.id = vol.pool_id WHERE pool.data_center_id = ?  AND pool.pod_id = ? AND pool.cluster_id = ? GROUP BY pool.id ORDER BY 2 ASC ";
        final long dcId = 1, accountId = 1;
        final Long podId = 1L, clusterId = 1L;

        when(TransactionLegacy.currentTxn()).thenReturn(transactionMock);
        when(transactionMock.prepareAutoCloseStatement(startsWith(ORDER_POOLS_NUMBER_OF_VOLUMES_FOR_ACCOUNT_QUERY_WITH_CLUSTER))).thenReturn(preparedStatementMock);
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(preparedStatementMock.executeQuery()).thenReturn(rs, rs);

        volumeDao.listPoolIdsByVolumeCount(dcId, podId, clusterId, accountId);

        verify(transactionMock, times(1)).prepareAutoCloseStatement(ORDER_POOLS_NUMBER_OF_VOLUMES_FOR_ACCOUNT_QUERY_WITH_CLUSTER);
        verify(preparedStatementMock, times(1)).setLong(1, accountId);
        verify(preparedStatementMock, times(1)).setLong(2, dcId);
        verify(preparedStatementMock, times(1)).setLong(3, podId);
        verify(preparedStatementMock, times(1)).setLong(4, clusterId);
        verify(preparedStatementMock, times(4)).setLong(anyInt(), anyLong());
        verify(preparedStatementMock, times(1)).executeQuery();
    }

    @Test
    public void testListPoolIdsByVolumeCount_without_cluster_details() throws SQLException {
        final String ORDER_POOLS_NUMBER_OF_VOLUMES_FOR_ACCOUNT_QUERY_WITHOUT_CLUSTER =
                "SELECT pool.id, SUM(IF(vol.state='Ready' AND vol.account_id = ?, 1, 0)) FROM `cloud`.`storage_pool` pool LEFT JOIN `cloud`.`volumes` vol ON pool.id = vol.pool_id WHERE pool.data_center_id = ?  GROUP BY pool.id ORDER BY 2 ASC ";
        final long dcId = 1, accountId = 1;

        when(TransactionLegacy.currentTxn()).thenReturn(transactionMock);
        when(transactionMock.prepareAutoCloseStatement(startsWith(ORDER_POOLS_NUMBER_OF_VOLUMES_FOR_ACCOUNT_QUERY_WITHOUT_CLUSTER))).thenReturn(preparedStatementMock);
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(preparedStatementMock.executeQuery()).thenReturn(rs, rs);

        volumeDao.listPoolIdsByVolumeCount(dcId, null, null, accountId);

        verify(transactionMock, times(1)).prepareAutoCloseStatement(ORDER_POOLS_NUMBER_OF_VOLUMES_FOR_ACCOUNT_QUERY_WITHOUT_CLUSTER);
        verify(preparedStatementMock, times(1)).setLong(1, accountId);
        verify(preparedStatementMock, times(1)).setLong(2, dcId);
        verify(preparedStatementMock, times(2)).setLong(anyInt(), anyLong());
        verify(preparedStatementMock, times(1)).executeQuery();
    }

    @Test
    public void findByInstanceAndNotState_queriesWithInstanceIdAndExcludedStates() {
        SearchBuilder<VolumeVO> sb = Mockito.mock(SearchBuilder.class);
        SearchCriteria<VolumeVO> sc = Mockito.mock(SearchCriteria.class);
        Mockito.when(sb.create()).thenReturn(sc);
        Mockito.doReturn(new ArrayList<>()).when(volumeDao).listBy(sc);
        Mockito.when(volumeDao.createSearchBuilder()).thenReturn(sb);
        VolumeVO mockedVO = Mockito.mock(VolumeVO.class);
        Mockito.when(sb.entity()).thenReturn(mockedVO);

        volumeDao.findByInstanceAndNotStates(42L, Volume.State.Ready);

        Mockito.verify(sc).setParameters("instanceId", 42L);
        Mockito.verify(sc).setParameters("state", (Object[]) new Volume.State[]{Volume.State.Ready});
    }

    @Test
    public void findByInstanceAndNotStates_withMultipleExcludedStates_passesAllStatesToCriteria() {
        SearchBuilder<VolumeVO> sb = Mockito.mock(SearchBuilder.class);
        SearchCriteria<VolumeVO> sc = Mockito.mock(SearchCriteria.class);
        Mockito.when(sb.create()).thenReturn(sc);
        Mockito.doReturn(new ArrayList<>()).when(volumeDao).listBy(sc);
        Mockito.when(volumeDao.createSearchBuilder()).thenReturn(sb);
        VolumeVO mockedVO = Mockito.mock(VolumeVO.class);
        Mockito.when(sb.entity()).thenReturn(mockedVO);

        volumeDao.findByInstanceAndNotStates(7L, Volume.State.Destroy, Volume.State.Expunged);

        Mockito.verify(sc).setParameters("instanceId", 7L);
        Mockito.verify(sc).setParameters("state",
                (Object[]) new Volume.State[]{Volume.State.Destroy, Volume.State.Expunged});
    }

    @Test
    public void findByInstanceAndNotStates_returnsResultFromDao() {
        SearchBuilder<VolumeVO> sb = Mockito.mock(SearchBuilder.class);
        SearchCriteria<VolumeVO> sc = Mockito.mock(SearchCriteria.class);
        Mockito.when(sb.create()).thenReturn(sc);
        VolumeVO vol = Mockito.mock(VolumeVO.class);
        Mockito.doReturn(List.of(vol)).when(volumeDao).listBy(sc);
        Mockito.when(volumeDao.createSearchBuilder()).thenReturn(sb);
        Mockito.when(sb.entity()).thenReturn(Mockito.mock(VolumeVO.class));

        List<VolumeVO> result = volumeDao.findByInstanceAndNotStates(1L, Volume.State.Ready);

        Assert.assertEquals(1, result.size());
        Assert.assertSame(vol, result.get(0));
    }

    @Test
    public void findByInstanceAndNotStates_noMatchingVolumes_returnsEmptyList() {
        SearchBuilder<VolumeVO> sb = Mockito.mock(SearchBuilder.class);
        SearchCriteria<VolumeVO> sc = Mockito.mock(SearchCriteria.class);
        Mockito.when(sb.create()).thenReturn(sc);
        Mockito.doReturn(new ArrayList<>()).when(volumeDao).listBy(sc);
        Mockito.when(volumeDao.createSearchBuilder()).thenReturn(sb);
        Mockito.when(sb.entity()).thenReturn(Mockito.mock(VolumeVO.class));

        List<VolumeVO> result = volumeDao.findByInstanceAndNotStates(99L, Volume.State.Ready);

        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testSearchRemovedByVmsNoVms() {
        Assert.assertTrue(CollectionUtils.isEmpty(volumeDao.searchRemovedByVms(
                new ArrayList<>(), 100L)));
        Assert.assertTrue(CollectionUtils.isEmpty(volumeDao.searchRemovedByVms(
                null, 100L)));
    }

    @Test
    public void testSearchRemovedByVms() {
        SearchBuilder<VolumeVO> sb = Mockito.mock(SearchBuilder.class);
        SearchCriteria<VolumeVO> sc = Mockito.mock(SearchCriteria.class);
        Mockito.when(sb.create()).thenReturn(sc);
        Mockito.doReturn(new ArrayList<>()).when(volumeDao).searchIncludingRemoved(
                Mockito.any(SearchCriteria.class), Mockito.any(Filter.class), Mockito.eq(null),
                Mockito.eq(false));
        Mockito.when(volumeDao.createSearchBuilder()).thenReturn(sb);
        final VolumeVO mockedVO = Mockito.mock(VolumeVO.class);
        Mockito.when(sb.entity()).thenReturn(mockedVO);
        List<Long> vmIds = List.of(1L, 2L);
        Object[] array = vmIds.toArray();
        Long batchSize = 50L;
        volumeDao.searchRemovedByVms(List.of(1L, 2L), batchSize);
        Mockito.verify(sc).setParameters("vmIds", array);
        Mockito.verify(volumeDao, Mockito.times(1)).searchIncludingRemoved(
                Mockito.any(SearchCriteria.class), Mockito.any(Filter.class), Mockito.eq(null),
                Mockito.eq(false));
    }

    private static final long DETACHED_VOLUME_ID = 1L;
    private static final long DETACHED_FROM_VM_ID = 2L;

    private void mockVolumeForConditionalDetach(Volume.Type existingType, int updatedRows) {
        VolumeVO existing = Mockito.mock(VolumeVO.class);
        Mockito.doReturn(existingType).when(existing).getVolumeType();
        Mockito.doReturn(existing).when(volumeDao).findById(DETACHED_VOLUME_ID);
        Mockito.doReturn(updatedRows).when(volumeDao).update(Mockito.any(VolumeVO.class), Mockito.<SearchCriteria<VolumeVO>>any());
    }

    @SuppressWarnings("unchecked")
    private SearchCriteria<VolumeVO> captureConditionalDetachCriteria() {
        ArgumentCaptor<SearchCriteria<VolumeVO>> criteria = ArgumentCaptor.forClass(SearchCriteria.class);
        Mockito.verify(volumeDao).update(Mockito.any(VolumeVO.class), criteria.capture());
        return criteria.getValue();
    }

    private List<Object> boundValuesOf(SearchCriteria<VolumeVO> criteria) {
        List<Object> values = new ArrayList<>();
        for (Pair<Attribute, Object> bound : criteria.getValues()) {
            values.add(bound.second());
        }
        return values;
    }

    @Test
    public void detachVolumeTestRestrictsTheUpdateToTheVmTheVolumeIsStillAttachedTo() {
        mockVolumeForConditionalDetach(Volume.Type.DATADISK, 1);

        volumeDao.detachVolume(DETACHED_VOLUME_ID, DETACHED_FROM_VM_ID);

        SearchCriteria<VolumeVO> criteria = captureConditionalDetachCriteria();
        Assert.assertTrue("the update must be restricted by instance_id, otherwise a stale detach clears a newer attachment; where clause was: "
                + criteria.getWhereClause(), criteria.getWhereClause().contains("instance_id"));
        List<Object> bound = boundValuesOf(criteria);
        Assert.assertEquals("expected the volume id and the vm id to be bound, got " + bound, 2, bound.size());
        Assert.assertTrue("the vm id must be bound, got " + bound, bound.contains(DETACHED_FROM_VM_ID));
        Assert.assertTrue("the volume id must be bound, got " + bound, bound.contains(DETACHED_VOLUME_ID));
    }

    @Test
    public void detachVolumeTestClearsAttachmentWhenStillAttachedToVm() {
        mockVolumeForConditionalDetach(Volume.Type.DATADISK, 1);

        Assert.assertTrue(volumeDao.detachVolume(DETACHED_VOLUME_ID, DETACHED_FROM_VM_ID));
    }

    @Test
    public void detachVolumeTestKeepsAttachmentWhenAttachedToAnotherVm() {
        mockVolumeForConditionalDetach(Volume.Type.DATADISK, 0);

        Assert.assertFalse(volumeDao.detachVolume(DETACHED_VOLUME_ID, DETACHED_FROM_VM_ID));
    }

    @Test
    public void detachVolumeTestConvertsADetachedRootVolumeToADataDisk() {
        mockVolumeForConditionalDetach(Volume.Type.ROOT, 1);

        volumeDao.detachVolume(DETACHED_VOLUME_ID, DETACHED_FROM_VM_ID);

        ArgumentCaptor<VolumeVO> updated = ArgumentCaptor.forClass(VolumeVO.class);
        Mockito.verify(volumeDao).update(updated.capture(), Mockito.<SearchCriteria<VolumeVO>>any());
        Assert.assertEquals(Volume.Type.DATADISK, updated.getValue().getVolumeType());
    }
}
