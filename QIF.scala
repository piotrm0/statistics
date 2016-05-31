import scala.math._

object QIF {
  def lg(i: Double): Double = log(i)/log(2)

  def entropy(freq: Seq[Long]): Double = {
    val total = freq.sum.toDouble

    freq.foldLeft(0.0: Double){ (a, f) =>
      a + information(f.toDouble / total)
    }
  }

  def information(p: Double): Double = if (p > 0) { - p * lg(p) } else { 0.0 }

  def minVul(freq: Seq[Long]): Double = {
    val total = freq.sum.toDouble
    val max = freq.max
    max / total
  }

  def minEntropy(freq: Seq[Long]): Double = - lg(minVul(freq))

  def condEntropy(freqs: Seq[Seq[Long]]): Double = {
    val withTotals = freqs.map{freq => (freq, freq.sum.toDouble)}
    val total = withTotals.map(_._2).sum

    withTotals.foldLeft(0.0) {
      case (a, (freq, tot)) =>
        a + (tot / total) * entropy(freq)
    }
  }
}
