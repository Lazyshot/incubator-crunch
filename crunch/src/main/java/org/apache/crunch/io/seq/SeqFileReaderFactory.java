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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.io.seq;

import java.io.IOException;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.crunch.MapFn;
import org.apache.crunch.io.FileReaderFactory;
import org.apache.crunch.io.impl.AutoClosingIterator;
import org.apache.crunch.types.Converter;
import org.apache.crunch.types.PTableType;
import org.apache.crunch.types.PType;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

class SeqFileReaderFactory<T> implements FileReaderFactory<T> {

  private static final Log LOG = LogFactory.getLog(SeqFileReaderFactory.class);

  private final Converter converter;
  private final MapFn<Object, T> mapFn;
  private final Writable key;
  private final Writable value;

  public SeqFileReaderFactory(PType<T> ptype) {
    this.converter = ptype.getConverter();
    this.mapFn = ptype.getInputMapFn();
    if (ptype instanceof PTableType) {
      PTableType ptt = (PTableType) ptype;
      this.key = SeqFileHelper.newInstance(ptt.getKeyType(), null);
      this.value = SeqFileHelper.newInstance(ptt.getValueType(), null);
    } else {
      this.key = NullWritable.get();
      this.value = SeqFileHelper.newInstance(ptype, null);
    }
  }

  @Override
  public Iterator<T> read(FileSystem fs, final Path path) {
    mapFn.initialize();
    try {
      final SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, fs.getConf());
      return new AutoClosingIterator<T>(reader, new UnmodifiableIterator<T>() {
        boolean nextChecked = false;
        boolean hasNext = false;

        @Override
        public boolean hasNext() {
          if (nextChecked == true) {
            return hasNext;
          }
          try {
            hasNext = reader.next(key, value);
            nextChecked = true;
            return hasNext;
          } catch (IOException e) {
            LOG.info("Error reading from path: " + path, e);
            return false;
          }
        }

        @Override
        public T next() {
          if (!nextChecked && !hasNext()) {
            return null;
          }
          nextChecked = false;
          return mapFn.map(converter.convertInput(key, value));
        }
      });
    } catch (IOException e) {
      LOG.info("Could not read seqfile at path: " + path, e);
      return Iterators.emptyIterator();
    }
  }

}
