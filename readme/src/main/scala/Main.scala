
object Main {
  val defaultArgs = Array(
    "--in", "README.template.md",
    "--out", "README.md"
  )
  def main(args: Array[String]): Unit = {
    val mdocArgs =
      if (args.startsWith("--no-default")) args.drop(1)
      else defaultArgs ++ args
    mdoc.Main.main(mdocArgs)
  }
}
