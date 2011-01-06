package com.cloud.network.ovs.dao;

import java.util.Date;
import java.util.List;
import javax.ejb.Local;
import com.cloud.ha.HaWorkVO;
import com.cloud.network.ovs.dao.OvsWorkVO.Step;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.exception.CloudRuntimeException;

@Local(value={OvsWorkDao.class})
public class OvsWorkDaoImpl extends GenericDaoBase<OvsWorkVO, Long> implements
		OvsWorkDao {
    private SearchBuilder<OvsWorkVO> VmIdTakenSearch;
    private SearchBuilder<OvsWorkVO> VmIdSeqNumSearch;
    private SearchBuilder<OvsWorkVO> VmIdUnTakenSearch;
    private SearchBuilder<OvsWorkVO> UntakenWorkSearch;
    private SearchBuilder<OvsWorkVO> VmIdStepSearch;
    private SearchBuilder<OvsWorkVO> CleanupSearch;


    protected OvsWorkDaoImpl() {
        VmIdTakenSearch = createSearchBuilder();
        VmIdTakenSearch.and("vmId", VmIdTakenSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        VmIdTakenSearch.and("taken", VmIdTakenSearch.entity().getDateTaken(), SearchCriteria.Op.NNULL);

        VmIdTakenSearch.done();
        
        VmIdUnTakenSearch = createSearchBuilder();
        VmIdUnTakenSearch.and("vmId", VmIdUnTakenSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        VmIdUnTakenSearch.and("taken", VmIdUnTakenSearch.entity().getDateTaken(), SearchCriteria.Op.NULL);

        VmIdUnTakenSearch.done();
        
        UntakenWorkSearch = createSearchBuilder();
        UntakenWorkSearch.and("server", UntakenWorkSearch.entity().getServerId(), SearchCriteria.Op.NULL);
        UntakenWorkSearch.and("taken", UntakenWorkSearch.entity().getDateTaken(), SearchCriteria.Op.NULL);
        UntakenWorkSearch.and("step", UntakenWorkSearch.entity().getStep(), SearchCriteria.Op.EQ);

        UntakenWorkSearch.done();
        
        VmIdSeqNumSearch = createSearchBuilder();
        VmIdSeqNumSearch.and("vmId", VmIdSeqNumSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        VmIdSeqNumSearch.and("seqno", VmIdSeqNumSearch.entity().getLogsequenceNumber(), SearchCriteria.Op.EQ);

        VmIdSeqNumSearch.done();
        
        VmIdStepSearch = createSearchBuilder();
        VmIdStepSearch.and("vmId", VmIdStepSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        VmIdStepSearch.and("step", VmIdStepSearch.entity().getStep(), SearchCriteria.Op.EQ);

        VmIdStepSearch.done();
        
        CleanupSearch = createSearchBuilder();
        CleanupSearch.and("taken", CleanupSearch.entity().getDateTaken(), Op.LTEQ);
        CleanupSearch.and("step", CleanupSearch.entity().getStep(), SearchCriteria.Op.IN);

        CleanupSearch.done();
        

    }

    @Override
    public OvsWorkVO findByVmId(long vmId, boolean taken) {
        SearchCriteria<OvsWorkVO> sc = taken?VmIdTakenSearch.create():VmIdUnTakenSearch.create();
        sc.setParameters("vmId", vmId);
        return findOneIncludingRemovedBy(sc);
    }

	@Override
	public OvsWorkVO take(long serverId) {
		final Transaction txn = Transaction.currentTxn();
        try {
            final SearchCriteria<OvsWorkVO> sc = UntakenWorkSearch.create();
            sc.setParameters("step", Step.Scheduled);

            final Filter filter = new Filter(OvsWorkVO.class, null, true, 0l, 1l);//FIXME: order desc by update time?

            txn.start();
            final List<OvsWorkVO> vos = lockRows(sc, filter, true);
            if (vos.size() == 0) {
                txn.commit();
                return null;
            }
            OvsWorkVO work = null;
            for (OvsWorkVO w: vos) {       
            	//ensure that there is no job in Processing state for the same VM
            	if ( findByVmIdStep(w.getInstanceId(), Step.Processing) == null) {
            		work = w;
            		break;
            	}
            }
            if (work == null) {
            	txn.commit();
            	return null;
            }
            work.setServerId(serverId);
            work.setDateTaken(new Date());
            work.setStep(OvsWorkVO.Step.Processing);

            update(work.getId(), work);

            txn.commit();

            return work;

        } catch (final Throwable e) {
            throw new CloudRuntimeException("Unable to execute take", e);
        }
	}

	@Override
	public void updateStep(Long vmId, Long logSequenceNumber, Step step) {
		final Transaction txn = Transaction.currentTxn();
		txn.start();
        SearchCriteria<OvsWorkVO> sc = VmIdSeqNumSearch.create();
        sc.setParameters("vmId", vmId);
        sc.setParameters("seqno", logSequenceNumber);
        
        final Filter filter = new Filter(HaWorkVO.class, null, true, 0l, 1l);

        final List<OvsWorkVO> vos = lockRows(sc, filter, true);
        if (vos.size() == 0) {
            txn.commit();
            return;
        }
        OvsWorkVO work = vos.get(0);
        work.setStep(step);
        update(work.getId(), work);

        txn.commit();
	}

	@Override
	public OvsWorkVO findByVmIdStep(long vmId, Step step) {
        SearchCriteria<OvsWorkVO> sc = VmIdStepSearch.create();
        sc.setParameters("vmId", vmId);
        sc.setParameters("step", step);
        return findOneIncludingRemovedBy(sc);
	}

	@Override
	public void updateStep(Long workId, Step step) {
		final Transaction txn = Transaction.currentTxn();
		txn.start();
        
		OvsWorkVO work = lockRow(workId, true);
        if (work == null) {
        	txn.commit();
        	return;
        }
        work.setStep(step);
        update(work.getId(), work);

        txn.commit();
		
	}

	@Override
	public int deleteFinishedWork(Date timeBefore) {
		final SearchCriteria<OvsWorkVO> sc = CleanupSearch.create();
		sc.setParameters("taken", timeBefore);
		sc.setParameters("step", Step.Done);

		return expunge(sc);
	}

	@Override
	public List<OvsWorkVO> findUnfinishedWork(Date timeBefore) {
		final SearchCriteria<OvsWorkVO> sc = CleanupSearch.create();
		sc.setParameters("taken", timeBefore);
		sc.setParameters("step", Step.Processing);

		List<OvsWorkVO> result = listIncludingRemovedBy(sc);
		
		OvsWorkVO work = createForUpdate();
		work.setStep(Step.Error);
		update(work, sc);
		
		return result;
	}
}
