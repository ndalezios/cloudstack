/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.cloud.storage.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.VMTemplateStorageResourceAssoc;
import com.cloud.storage.VMTemplateStorageResourceAssoc.Status;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;

@Local(value={VMTemplatePoolDao.class})
public class VMTemplatePoolDaoImpl extends GenericDaoBase<VMTemplateStoragePoolVO, Long> implements VMTemplatePoolDao {
	public static final Logger s_logger = Logger.getLogger(VMTemplatePoolDaoImpl.class.getName());
	
	protected final SearchBuilder<VMTemplateStoragePoolVO> PoolSearch;
	protected final SearchBuilder<VMTemplateStoragePoolVO> TemplateSearch;
	protected final SearchBuilder<VMTemplateStoragePoolVO> PoolTemplateSearch;
	protected final SearchBuilder<VMTemplateStoragePoolVO> TemplateStatusSearch;
    protected final SearchBuilder<VMTemplateStoragePoolVO> TemplatePoolStatusSearch;
	protected final SearchBuilder<VMTemplateStoragePoolVO> TemplateStatesSearch;
	
	protected static final String UPDATE_TEMPLATE_HOST_REF =
		"UPDATE template_spool_ref SET download_state = ?, download_pct= ?, last_updated = ? "
	+   ", error_str = ?, local_path = ?, job_id = ? "
	+   "WHERE pool_id = ? and template_id = ?";
	
	protected static final String DOWNLOADS_STATE_DC=
		"SELECT * FROM template_spool_ref t, storage_pool p where t.pool_id = p.id and p.data_center_id=? "
	+	" and t.template_id=? and t.download_state = ?" ;
	
	protected static final String DOWNLOADS_STATE_DC_POD=
		"SELECT * FROM template_spool_ref tp, storage_pool_host_ref ph, host h where tp.pool_id = ph.pool_id and ph.host_id = h.id and h.data_center_id=? and h.pod_id=? "
	+	" and tp.template_id=? and tp.download_state=?" ;
	
	protected static final String HOST_TEMPLATE_SEARCH=
		"SELECT * FROM template_spool_ref tp, storage_pool_host_ref ph, host h where tp.pool_id = ph.pool_id and ph.host_id = h.id and h.id=? "
	+	" and tp.template_id=? " ;
	
	
	public VMTemplatePoolDaoImpl () {
		PoolSearch = createSearchBuilder();
		PoolSearch.and("pool_id", PoolSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
		PoolSearch.done();
		
		TemplateSearch = createSearchBuilder();
		TemplateSearch.and("template_id", TemplateSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
		TemplateSearch.done();
		
		PoolTemplateSearch = createSearchBuilder();
		PoolTemplateSearch.and("pool_id", PoolTemplateSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
		PoolTemplateSearch.and("template_id", PoolTemplateSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
		PoolTemplateSearch.done();
		
		TemplateStatusSearch = createSearchBuilder();
		TemplateStatusSearch.and("template_id", TemplateStatusSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
		TemplateStatusSearch.and("download_state", TemplateStatusSearch.entity().getDownloadState(), SearchCriteria.Op.EQ);
		TemplateStatusSearch.done();

		TemplatePoolStatusSearch = createSearchBuilder();
		TemplatePoolStatusSearch.and("pool_id", TemplatePoolStatusSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
		TemplatePoolStatusSearch.and("template_id", TemplatePoolStatusSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
		TemplatePoolStatusSearch.and("download_state", TemplatePoolStatusSearch.entity().getDownloadState(), SearchCriteria.Op.EQ);
		TemplatePoolStatusSearch.done();

        TemplateStatesSearch = createSearchBuilder();
		TemplateStatesSearch.and("template_id", TemplateStatesSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
		TemplateStatesSearch.and("states", TemplateStatesSearch.entity().getDownloadState(), SearchCriteria.Op.IN);
		TemplateStatesSearch.done();
	}

	@Override
	public List<VMTemplateStoragePoolVO> listByPoolId(long id) {
	    SearchCriteria sc = PoolSearch.create();
	    sc.setParameters("pool_id", id);
	    return listBy(sc);
	}

	@Override
	public List<VMTemplateStoragePoolVO> listByTemplateId(long templateId) {
	    SearchCriteria sc = TemplateSearch.create();
	    sc.setParameters("template_id", templateId);
	    return listBy(sc);
	}

	@Override
	public VMTemplateStoragePoolVO findByPoolTemplate(long hostId, long templateId) {
		SearchCriteria sc = PoolTemplateSearch.create();
	    sc.setParameters("pool_id", hostId);
	    sc.setParameters("template_id", templateId);
	    return findOneBy(sc);
	}

	@Override
	public List<VMTemplateStoragePoolVO> listByTemplateStatus(long templateId, VMTemplateStoragePoolVO.Status downloadState) {
		SearchCriteria sc = TemplateStatusSearch.create();
		sc.setParameters("template_id", templateId);
		sc.setParameters("download_state", downloadState.toString());
		return listBy(sc);
	}

	@Override
    public List<VMTemplateStoragePoolVO> listByTemplateStatus(long templateId, VMTemplateStoragePoolVO.Status downloadState, long poolId) {
        SearchCriteria sc = TemplatePoolStatusSearch.create();
        sc.setParameters("pool_id", poolId);
        sc.setParameters("template_id", templateId);
        sc.setParameters("download_state", downloadState.toString());
        return listBy(sc);
    }

	@Override
	public List<VMTemplateStoragePoolVO> listByTemplateStatus(long templateId, long datacenterId, VMTemplateStoragePoolVO.Status downloadState) {
        Transaction txn = Transaction.currentTxn();
		PreparedStatement pstmt = null;
		List<VMTemplateStoragePoolVO> result = new ArrayList<VMTemplateStoragePoolVO>();
		try {
			String sql = DOWNLOADS_STATE_DC;
			pstmt = txn.prepareAutoCloseStatement(sql);
			pstmt.setLong(1, datacenterId);
			pstmt.setLong(2, templateId);
			pstmt.setString(3, downloadState.toString());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
                result.add(toEntityBean(rs, false));
            }
		} catch (Exception e) {
			s_logger.warn("Exception: ", e);
		}
		return result;

	}
	
	@Override
	public List<VMTemplateStoragePoolVO> listByTemplateStatus(long templateId, long datacenterId, long podId, VMTemplateStoragePoolVO.Status downloadState) {
        Transaction txn = Transaction.currentTxn();
		PreparedStatement pstmt = null;
		List<VMTemplateStoragePoolVO> result = new ArrayList<VMTemplateStoragePoolVO>();
		ResultSet rs = null;
		try {
			String sql = DOWNLOADS_STATE_DC_POD;
			pstmt = txn.prepareStatement(sql);
			
			pstmt.setLong(1, datacenterId);
			pstmt.setLong(2, podId);
			pstmt.setLong(3, templateId);
			pstmt.setString(4, downloadState.toString());
			rs = pstmt.executeQuery();
			while (rs.next()) {
                // result.add(toEntityBean(rs, false)); TODO: this is buggy in GenericDaoBase for hand constructed queries
				long id = rs.getLong(1); //ID column
				result.add(findById(id));
            }
		} catch (Exception e) {
			s_logger.warn("Exception: ", e);
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		return result;

	}
	
	public List<VMTemplateStoragePoolVO> listByHostTemplate(long hostId, long templateId) {
        Transaction txn = Transaction.currentTxn();
		PreparedStatement pstmt = null;
		List<VMTemplateStoragePoolVO> result = new ArrayList<VMTemplateStoragePoolVO>();
		ResultSet rs = null;
		try {
			String sql = HOST_TEMPLATE_SEARCH;
			pstmt = txn.prepareStatement(sql);
			
			pstmt.setLong(1, hostId);
			pstmt.setLong(2, templateId);
			rs = pstmt.executeQuery();
			while (rs.next()) {
                // result.add(toEntityBean(rs, false)); TODO: this is buggy in GenericDaoBase for hand constructed queries
				long id = rs.getLong(1); //ID column
				result.add(findById(id));
            }
		} catch (Exception e) {
			s_logger.warn("Exception: ", e);
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		return result;

	}

	@Override
	public boolean templateAvailable(long templateId, long hostId) {
		VMTemplateStorageResourceAssoc tmpltPool = findByPoolTemplate(hostId, templateId);
		if (tmpltPool == null)
		  return false;
		
		return tmpltPool.getDownloadState()==Status.DOWNLOADED;
	}

	@Override
	public List<VMTemplateStoragePoolVO> listByTemplateStates(long templateId, VMTemplateStoragePoolVO.Status... states) {
    	SearchCriteria sc = TemplateStatesSearch.create();
    	sc.setParameters("states", (Object[])states);
		sc.setParameters("template_id", templateId);

	  	return search(sc, null);
	}

	@Override
	public VMTemplateStoragePoolVO findByHostTemplate(Long hostId, Long templateId) {
		List<VMTemplateStoragePoolVO> result = listByHostTemplate(hostId, templateId);
		return (result.size() == 0)?null:result.get(1);
	}

}
