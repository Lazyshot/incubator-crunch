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
package org.apache.crunch.types.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.mapred.AvroJob;
import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapred.AvroValue;
import org.apache.avro.mapred.AvroWrapper;
import org.apache.avro.mapred.Pair;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.Serialization;
import org.apache.hadoop.io.serializer.Serializer;
import org.apache.hadoop.util.ReflectionUtils;

/** The {@link Serialization} used by jobs configured with {@link AvroJob}. */
class SafeAvroSerialization<T> extends Configured implements Serialization<AvroWrapper<T>> {

  public boolean accept(Class<?> c) {
    return AvroWrapper.class.isAssignableFrom(c);
  }

  /**
   * Returns the specified map output deserializer. Defaults to the final output
   * deserializer if no map output schema was specified.
   */
  public Deserializer<AvroWrapper<T>> getDeserializer(Class<AvroWrapper<T>> c) {
    boolean isKey = AvroKey.class.isAssignableFrom(c);
    Configuration conf = getConf();
    Schema schema = isKey ? Pair.getKeySchema(AvroJob.getMapOutputSchema(conf)) : Pair.getValueSchema(AvroJob
        .getMapOutputSchema(conf));

    DatumReader<T> datumReader = null;
    if (conf.getBoolean(AvroJob.MAP_OUTPUT_IS_REFLECT, false)) {
      ReflectDataFactory factory = (ReflectDataFactory) ReflectionUtils.newInstance(
          conf.getClass("crunch.reflectdatafactory", ReflectDataFactory.class), conf);
      datumReader = factory.getReader(schema);
    } else {
      datumReader = new SpecificDatumReader<T>(schema);
    }
    return new AvroWrapperDeserializer(datumReader, isKey);
  }

  private static final DecoderFactory FACTORY = DecoderFactory.get();

  private class AvroWrapperDeserializer implements Deserializer<AvroWrapper<T>> {

    private DatumReader<T> reader;
    private BinaryDecoder decoder;
    private boolean isKey;

    public AvroWrapperDeserializer(DatumReader<T> reader, boolean isKey) {
      this.reader = reader;
      this.isKey = isKey;
    }

    public void open(InputStream in) {
      this.decoder = FACTORY.directBinaryDecoder(in, decoder);
    }

    public AvroWrapper<T> deserialize(AvroWrapper<T> wrapper) throws IOException {
      T datum = reader.read(wrapper == null ? null : wrapper.datum(), decoder);
      if (wrapper == null) {
        wrapper = isKey ? new AvroKey<T>(datum) : new AvroValue<T>(datum);
      } else {
        wrapper.datum(datum);
      }
      return wrapper;
    }

    public void close() throws IOException {
      decoder.inputStream().close();
    }
  }

  /** Returns the specified output serializer. */
  public Serializer<AvroWrapper<T>> getSerializer(Class<AvroWrapper<T>> c) {
    // AvroWrapper used for final output, AvroKey or AvroValue for map output
    boolean isFinalOutput = c.equals(AvroWrapper.class);
    Configuration conf = getConf();
    Schema schema = isFinalOutput ? AvroJob.getOutputSchema(conf) : (AvroKey.class.isAssignableFrom(c) ? Pair
        .getKeySchema(AvroJob.getMapOutputSchema(conf)) : Pair.getValueSchema(AvroJob.getMapOutputSchema(conf)));

    ReflectDataFactory factory = Avros.getReflectDataFactory(conf);
    ReflectDatumWriter<T> writer = factory.getWriter(schema);
    return new AvroWrapperSerializer(writer);
  }

  private class AvroWrapperSerializer implements Serializer<AvroWrapper<T>> {
    private DatumWriter<T> writer;
    private OutputStream out;
    private BinaryEncoder encoder;

    public AvroWrapperSerializer(DatumWriter<T> writer) {
      this.writer = writer;
    }

    public void open(OutputStream out) {
      this.out = out;
      this.encoder = new EncoderFactory().configureBlockSize(512).binaryEncoder(out, null);
    }

    public void serialize(AvroWrapper<T> wrapper) throws IOException {
      writer.write(wrapper.datum(), encoder);
      // would be a lot faster if the Serializer interface had a flush()
      // method and the Hadoop framework called it when needed rather
      // than for every record.
      encoder.flush();
    }

    public void close() throws IOException {
      out.close();
    }
  }

}