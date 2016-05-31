//import collection.JavaConversions._

import java.nio.file.{Path,FileSystems}
import java.io.File

import scala.sys.process._

object Dirs {
  def toFile(p: Path) = new File(p.toString)

  val dotExe   = Process("which dot"  ).!!.trim
  val javaExe  = Process("which java" ).!!.trim
  val javapExe = Process("which javap").!!.trim

  val fs   = FileSystems.getDefault
  val base = FileSystems.getDefault.getPath(".cache")
  base.toFile.mkdirs

  val tmp = base.resolve("tmp")
  tmp.toFile.mkdirs

  def subTmpDir(p: Path): Path = {
    subTmpDir(p.getFileName.toString)
  }

  def subTmpDir(n: String): Path = {
    val temp = n.replace("/", ".")
    val newDir = tmp.resolve(temp)
    newDir.toFile.mkdirs
    newDir
  }
}
