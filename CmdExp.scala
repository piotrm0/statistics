import collection.JavaConversions._

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.ml.regression._
import org.apache.spark.mllib.linalg._

//import scala.collection.mutable.ArrayBuffer
//import scala.collection.mutable._
//import scala.collection._

import scala.util.Random

import org.apache.log4j.{Logger,Level}

object Global {
  Logger.getLogger("Remoting")  .setLevel(Level.ERROR)
  Logger.getLogger("org")       .setLevel(Level.ERROR)
  Logger.getLogger("akka")      .setLevel(Level.ERROR)
  Logger.getLogger("spark")     .setLevel(Level.ERROR)
  Logger.getLogger("spark")     .setLevel(Level.ERROR)
  Logger.getLogger("databricks").setLevel(Level.ERROR)
  Logger.getRootLogger()        .setLevel(Level.ERROR)

  val conf = new SparkConf()
    .setMaster("local[8]")
    .setAppName("Trees")
  val sc = new SparkContext(conf)
  val sqlContext = new SQLContext(sc)

  var r = new Random(0)
}

object Correlation {
  abstract class Correlation(val name: String) {
    override def toString = name
  }
  case class Positive() extends Correlation("+")
  case class Negative() extends Correlation("-")
  case class Not()      extends Correlation("o")

  def rand: Correlation = {
    val r = Global.r.nextDouble % 1.0
    if (r < 1.0/3.0) {
      Positive()
    } else if (r < 2.0/3.0) {
      Negative()
    } else {
      Not()
    }
  }

}

import Correlation._

abstract class Strategy(val correctPoints: Double, val incorrectPenalty: Double) {
  def score(actual: List[Correlation], preds: List[Option[Correlation]]): Double =
    actual.zip(preds).foldLeft(0.0) { case (acc, (a,p)) =>
      p match {
        case Some(p) => if (a == p) { acc + correctPoints } else { acc - incorrectPenalty }
        case None => acc + 0.0
      }
    }

  def predict(df: DataFrame): List[Option[Correlation]]
}

class AllTThresh(val tThresh: Double, _c: Double, _i: Double) extends Strategy(_c, _i) {
  def predict(df: DataFrame): List[Option[Correlation]] = {
    val reg = new LinearRegression()
    reg.setLabelCol("dep")
    reg.setFeaturesCol("indep")

    val model   = reg.fit(df)
    val summary = model.summary

    summary.tValues.zip(summary.pValues).map{ case (t,p) =>
      if (t >= tThresh) {
        Some(Positive())
      } else if (t <= -tThresh) {
        Some(Negative())
      } else {
        None
      }
    }.dropRight(1).toList

      /*println("coeffs = " + model.coefficients)
       println("r2 = " + summary.r2)
       println("t  = " + summary.tValues.mkString(","))
       println("p  = " + summary.pValues.mkString(","))
       println("\n")*/

  }
}

class AllPThresh(val pThresh: Double, _c: Double, _i: Double) extends Strategy(_c, _i) {
  def predict(df: DataFrame): List[Option[Correlation]] = {
    val reg = new LinearRegression()
    reg.setLabelCol("dep")
    reg.setFeaturesCol("indep")

    val model   = reg.fit(df)
    val summary = model.summary

    //println(summary.tValues.zip(summary.pValues).mkString("\n"))

    summary.pValues.zip(summary.tValues).dropRight(1).map{ case (p,t) =>
      if (p <= pThresh) {
        if (t > 0) {
          Some(Positive())
        } else {
          Some(Negative())
        }
      } else {
        None
      }
    }.toList
  }
}

class SomeTThresh(val howMany: Int, val tThresh: Double, _c: Double, _i: Double) extends Strategy(_c, _i) {
  def predict(df: DataFrame): List[Option[Correlation]] = {
    val reg = new LinearRegression()
    reg.setLabelCol("dep")
    reg.setFeaturesCol("indep")

    val model   = reg.fit(df)
    val summary = model.summary

    summary.tValues.zipWithIndex.map{ case (t,i) =>
      if (i >= howMany) {
        None
      } else {
        if (t >= tThresh) {
          Some(Positive())
        } else if (t <= -tThresh) {
          Some(Negative())
        } else {
          None
        }
      }
    }.dropRight(1).toList
  }
}

class BestTThresh(val numBest: Int, val tThresh: Double, _c: Double, _i: Double) extends Strategy(_c, _i) {
  def predict(df: DataFrame): List[Option[Correlation]] = {
    val reg = new LinearRegression()
    reg.setLabelCol("dep")
    reg.setFeaturesCol("indep")

    val model   = reg.fit(df)
    val summary = model.summary

    val temp = summary.tValues.dropRight(1)
    val maxTtemp = temp.map{_.abs}.sorted.reverse.take(numBest).reverse
    //println("maxT = " + maxTtemp.mkString(","))
    val maxT = maxTtemp(0)

    //println("temp = " + temp.mkString(","))
    //println("maxT = " + maxT)

    temp.map{ case t =>
      if (t >= maxT && t >= tThresh) {
        Some(Positive())
      } else if (t <= -maxT && t <= -tThresh) {
        Some(Negative())
      } else {
        None
      }
    }.toList
  }
}

object Evaluate {
 
}

object Instance {
  val schema = StructType(
    StructField("indep", new VectorUDT(), false) ::
    StructField("dep", DoubleType, false) :: Nil
  )

  def toFrame(inseq: Seq[Instance]): DataFrame = {
    Global.sqlContext.createDataFrame(rows = inseq.map{_.toRow}.toList, schema = schema)
  }

  def genInstances(
    num: Int,
    withDep: List[Correlation],
    depNoise: Double
  ): DataFrame = {
    val instances = 0.until(num).map{ i =>
      val (indep, dep) = withDep.foldLeft(List[Double](), 0.0) {
        case ((accIndep, accDep), isDep) =>
          val indep = Global.r.nextDouble % 1.0
          val noise = Global.r.nextGaussian * depNoise
          val dep = isDep match {
            case Positive() => indep + noise
            case Negative() => (1 - indep) + noise
            case Not()      => 0.5 + noise
          }
          (indep :: accIndep, dep + accDep)
      }
      Instance(indep.reverse, dep)
    }
    toFrame(instances)
  }
}

case class Instance(val indep: List[Double], val dep: Double) {
  def toRow: Row = Row(new DenseVector(indep.toArray), dep)
}

object Eval {
  def evalStrat(
    strat: Strategy,
    numFeats: Int,
    instances: Int,
    depNoise: Double,
    cPoints: Double,
    iPenalty: Double
  ): Double = {

    val iters = 20
    var tot = 0.0

    0.until(iters).foreach { i =>
      val isDep = 0.until(numFeats).map { _ => Correlation.rand }.toList
      val datas = Instance.genInstances(instances, isDep, depNoise)

      val preds = strat.predict(datas)
      val score = strat.score(isDep, preds)

      //println("isDep = " + isDep)
      //println("preds = " + preds)
      //println("score = " + score)
      //println("\n")
      tot = tot + score
    }

    tot / iters.toDouble
  }
}

object Exp extends App {
  val numFeats = 20

  val cPoints  = 1
  val iPenalty = 100

  println("thresh\tsingleTThres\tallTThres\tbestTThres")

  var i = 0

  (1.0 to 10.0 by 0.1).foreach { thresh =>
    i = i + 1
    val singleTThresh = new SomeTThresh(1, thresh, cPoints, iPenalty)
    val allTThresh    = new AllTThresh(thresh, cPoints, iPenalty)
    //val allPThresh    = new AllPThresh(1.0/(100.0 * thresh), cPoints, iPenalty)
    val bestTThresh   = new BestTThresh(3, thresh, cPoints, iPenalty)
    Global.r = new Random(i)
    val eScore1 = Eval.evalStrat(singleTThresh, 20, 100, 0.2, cPoints, iPenalty)
    Global.r = new Random(i)
    val eScore2 = Eval.evalStrat(allTThresh,    20, 100, 0.2, cPoints, iPenalty)
    Global.r = new Random(i)
    //val eScore3 = Eval.evalStrat(allPThresh,    20, 100, 0.2, cPoints, iPenalty)
    val eScore4 = Eval.evalStrat(bestTThresh,   20, 100, 0.2, cPoints, iPenalty)
    println(thresh + "\t" + eScore1 + "\t" + eScore2 + "\t" + eScore4)
  }

}
