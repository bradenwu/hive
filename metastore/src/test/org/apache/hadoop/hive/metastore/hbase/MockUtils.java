/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hadoop.hive.metastore.hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hive.conf.HiveConf;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Mock utilities for HBaseStore testing
 */
public class MockUtils {

  static HBaseStore init(Configuration conf, HTableInterface htable,
                         final SortedMap<String, Cell> rows) throws IOException {
    Mockito.when(htable.get(Mockito.any(Get.class))).thenAnswer(new Answer<Result>() {
      @Override
      public Result answer(InvocationOnMock invocation) throws Throwable {
        Get get = (Get) invocation.getArguments()[0];
        Cell cell = rows.get(new String(get.getRow()));
        if (cell == null) {
          return new Result();
        } else {
          return Result.create(new Cell[]{cell});
        }
      }
    });

    Mockito.when(htable.getScanner(Mockito.any(Scan.class))).thenAnswer(new Answer<ResultScanner>() {
      @Override
      public ResultScanner answer(InvocationOnMock invocation) throws Throwable {
        Scan scan = (Scan)invocation.getArguments()[0];
        List<Result> results = new ArrayList<Result>();
        SortedMap<String, Cell> sub =
            rows.subMap(new String(scan.getStartRow()), new String(scan.getStopRow()));
        for (Map.Entry<String, Cell> e : sub.entrySet()) {
          results.add(Result.create(new Cell[]{e.getValue()}));
        }

        final Iterator<Result> iter = results.iterator();

        return new ResultScanner() {
          @Override
          public Result next() throws IOException {
            return null;
          }

          @Override
          public Result[] next(int nbRows) throws IOException {
            return new Result[0];
          }

          @Override
          public void close() {

          }

          @Override
          public Iterator<Result> iterator() {
            return iter;
          }
        };
      }
    });

    Mockito.doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        Put put = (Put)invocation.getArguments()[0];
        rows.put(new String(put.getRow()), put.getFamilyCellMap().firstEntry().getValue().get(0));
        return null;
      }
    }).when(htable).put(Mockito.any(Put.class));

    Mockito.when(htable.checkAndPut(Mockito.any(byte[].class), Mockito.any(byte[].class),
        Mockito.any(byte[].class), Mockito.any(byte[].class), Mockito.any(Put.class))).thenAnswer(
        new Answer<Boolean>() {

          @Override
          public Boolean answer(InvocationOnMock invocation) throws Throwable {
            // Always say it succeeded and overwrite
            Put put = (Put)invocation.getArguments()[4];
            rows.put(new String(put.getRow()),
                put.getFamilyCellMap().firstEntry().getValue().get(0));
            return true;
          }
        });

    Mockito.doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        Delete del = (Delete)invocation.getArguments()[0];
        rows.remove(new String(del.getRow()));
        return null;
      }
    }).when(htable).delete(Mockito.any(Delete.class));

    Mockito.when(htable.checkAndDelete(Mockito.any(byte[].class), Mockito.any(byte[].class),
        Mockito.any(byte[].class), Mockito.any(byte[].class), Mockito.any(Delete.class))).thenAnswer(
        new Answer<Boolean>() {

          @Override
          public Boolean answer(InvocationOnMock invocation) throws Throwable {
            // Always say it succeeded
            Delete del = (Delete)invocation.getArguments()[4];
            rows.remove(new String(del.getRow()));
            return true;
          }
        });

    // Mock connection
    HBaseConnection hconn = Mockito.mock(HBaseConnection.class);
    Mockito.when(hconn.getHBaseTable(Mockito.anyString())).thenReturn(htable);
    HiveConf.setVar(conf, HiveConf.ConfVars.METASTORE_HBASE_CONNECTION_CLASS, HBaseReadWrite.TEST_CONN);
    HBaseReadWrite hbase = HBaseReadWrite.getInstance(conf);
    hbase.setConnection(hconn);
    HBaseStore store = new HBaseStore();
    store.setConf(conf);
    return store;
  }
}