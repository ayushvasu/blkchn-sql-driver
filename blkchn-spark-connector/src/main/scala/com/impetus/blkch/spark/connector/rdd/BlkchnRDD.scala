/*******************************************************************************
* * Copyright 2018 Impetus Infotech.
* *
* * Licensed under the Apache License, Version 2.0 (the "License");
* * you may not use this file except in compliance with the License.
* * You may obtain a copy of the License at
* *
* * http://www.apache.org/licenses/LICENSE-2.0
* *
* * Unless required by applicable law or agreed to in writing, software
* * distributed under the License is distributed on an "AS IS" BASIS,
* * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* * See the License for the specific language governing permissions and
* * limitations under the License.
******************************************************************************/
package com.impetus.blkch.spark.connector.rdd

import java.math.BigInteger
import java.sql.ResultSetMetaData
import java.sql.Types

import scala.reflect.ClassTag

import org.apache.spark.Partition
import org.apache.spark.SparkContext
import org.apache.spark.TaskContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.types._

import com.impetus.blkch.spark.connector.BlkchnConnector
import com.impetus.blkch.spark.connector.rdd.partitioner.BlkchnPartition
import com.impetus.blkch.BlkchnException

class BlkchnRDD[R: ClassTag](@transient sc: SparkContext,
                             private[impetus] val connector: Broadcast[BlkchnConnector],
                             private[impetus] val readConf: ReadConf) extends RDD[R](sc, Nil) {

  override protected def getPartitions: Array[Partition] = {
    readConf.partitioner.getPartitions(connector.value, readConf).asInstanceOf[Array[Partition]]
  }

  def getSchema(meta: ResultSetMetaData = null):StructType = {
    val metadata = if(meta != null) meta else connector.value.withStatementDo {
      stat =>
        stat.getSchema(readConf.query)
    }
    val columnCount = metadata.getColumnCount
    val schema = for(i <- 1 to columnCount) yield {
      getStructField(i, metadata)
    }
    StructType(schema)
  }

  override def compute(split: Partition, context: TaskContext): Iterator[R] = {
    connector.value.withStatementDo {
      stat =>
        val partition = split.asInstanceOf[BlkchnPartition]
        stat.setPageRange(partition.range)
        val rs = stat.executeQuery(partition.readConf.query)
        var buffer = scala.collection.mutable.ArrayBuffer[R]()
        val metadata = rs.getMetaData
        val columnCount = metadata.getColumnCount
        val schema = getSchema(metadata)
        while(rs.next()) {
          val rowVals = (for(i <- 1 to columnCount) yield {
            if(rs.getObject(i).isInstanceOf[BigInteger]){
              val dataValue = new BigDecimal(rs.getBigDecimal(i))
              dataValue
            } else if(rs.getObject(i).isInstanceOf[java.util.ArrayList[_]]) {
              handleExtraData(i, metadata, rs.getObject(i))
            } else if(rs.getObject(i).isInstanceOf[java.sql.Array]) {
              rs.getObject(i).asInstanceOf[java.sql.Array].getArray
            } else {
              rs.getObject(i).asInstanceOf[Any]
            }
          }).toArray
          buffer = buffer :+ new GenericRowWithSchema(rowVals, StructType(schema)).asInstanceOf[R]
        }
        buffer.toIterator
    }
  }

  private def getStructField(index: Int, metadata: ResultSetMetaData): StructField = {
    val dataType = metadata.getColumnType(index) match {
      case Types.INTEGER => IntegerType
      case Types.DOUBLE => DoubleType
      case Types.BIGINT => return handleExtraType(index, metadata)
      case Types.FLOAT => FloatType
      case Types.BOOLEAN => BooleanType
      case Types.TIMESTAMP => TimestampType
      case Types.JAVA_OBJECT => return handleExtraType(index, metadata)
      case Types.ARRAY => return handleArrayType(index, metadata)
      case _ => StringType
    }
    StructField(metadata.getColumnLabel(index), dataType, true)
  }

  def handleExtraType(index: Int, metadata: ResultSetMetaData): StructField = if(metadata.getColumnType(index).equals(Types.BIGINT)) {
    StructField(metadata.getColumnLabel(index), LongType, true)
  } else {
    StructField(metadata.getColumnLabel(index), StringType, true)
  }

  def handleExtraData(index: Int, metadata: ResultSetMetaData,data: java.lang.Object): Any = ???

  def handleArrayType(index: Int, metadata: ResultSetMetaData): StructField = {
    val column = metadata.getColumnName(index)
    val table = metadata.getTableName(index)
    connector.value.withStatementDo {
      stat =>
        stat.getArrayElementType(table, column)
    } match {
      case Types.VARCHAR => StructField(metadata.getColumnLabel(index),
        DataTypes.createArrayType(StringType), true)

      case Types.INTEGER => StructField(metadata.getColumnLabel(index),
        DataTypes.createArrayType(IntegerType), true)

      case Types.BIGINT => StructField(metadata.getColumnLabel(index),
        DataTypes.createArrayType(LongType), true)

      case Types.DOUBLE => StructField(metadata.getColumnLabel(index),
        DataTypes.createArrayType(DoubleType), true)

      case Types.FLOAT => StructField(metadata.getColumnLabel(index),
        DataTypes.createArrayType(FloatType), true)

      case Types.BOOLEAN => StructField(metadata.getColumnLabel(index),
        DataTypes.createArrayType(BooleanType), true)

      case _ => throw new BlkchnException("Unidentified type found for column " +
        metadata.getColumnLabel(index));
    }
  }
}

