package com.xz.hbase.endpoint.statisticImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.coprocessor.CoprocessorException;
import org.apache.hadoop.hbase.coprocessor.CoprocessorService;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.protobuf.ResponseConverter;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.util.Bytes;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.Service;
import com.xz.hbase.endpoint.protocol.Statistics.CountRequest;
import com.xz.hbase.endpoint.protocol.Statistics.CountResponse;
import com.xz.hbase.endpoint.protocol.Statistics.RowKeyCountService;

public class RowKeyCountImpl extends RowKeyCountService implements Coprocessor,
		CoprocessorService {
	private RegionCoprocessorEnvironment env;

	@Override
	public Service getService() {
		return this;
	}

	@Override
	public void start(CoprocessorEnvironment env) throws IOException {
		if (env instanceof RegionCoprocessorEnvironment) {
			this.env = (RegionCoprocessorEnvironment) env;
		} else {
			throw new CoprocessorException("must be load on a table region");
		}
	}

	@Override
	public void stop(CoprocessorEnvironment arg0) throws IOException {
	}

	@Override
	public void getRowCount(RpcController controller, CountRequest request,
			RpcCallback<CountResponse> done) {
		Scan scan = new Scan();
		String startKey = request.getStartKey();
		String endKey = request.getEndKey();
		// 使用FirstKeyOnlyFilter 只取每个rowkey 下的第一个列族
		Filter filter = new FirstKeyOnlyFilter();
		scan.setFilter(filter);
		if (startKey!=null && !startKey.equals("")) {
			scan.setStartRow(Bytes.toBytes(startKey)) ;
		}
		if (endKey!=null && !endKey.equals("")) {
			scan.setStopRow(Bytes.toBytes(endKey)) ;
		}
		CountResponse rowCountResponse = null;
		RegionScanner regionScanner = null;
		try {
			regionScanner = env.getRegion().getScanner(scan) ;
			List<Cell> results = new ArrayList<>() ;
			boolean hasMore = false ;
			byte[] lastRow = null ;
			long count = 0 ;
			do {
				hasMore = regionScanner.next(results) ;
				for (int i = 0; i < results.size(); i++) {
					Cell kv = results.get(i) ;
					byte[] currentRow = CellUtil.cloneRow(kv) ;
					if (lastRow == null || !Bytes.equals(lastRow, currentRow)) {
						lastRow = currentRow ;
						count++ ;
					}
				}
				results.clear();
			} while (hasMore);
			rowCountResponse = CountResponse.newBuilder().setCount(count).build() ;
		} catch (IOException e) {
			ResponseConverter.setControllerException(controller, e);
		}finally {
			if (regionScanner!=null) {
				try {
					regionScanner.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		done.run(rowCountResponse);
	}

}
