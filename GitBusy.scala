import java.io.{BufferedWriter, PrintWriter, OutputStreamWriter}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Random
import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.io.Source

object GitBusy {
  object Config {
    val startDate = LocalDate.of(2020,2,1)
    val stopDate = LocalDate.of(2022,1,31)
    val startTime = LocalTime.of(8,0,0).toSecondOfDay()
    val stopAfter = {
      val stopTime = LocalTime.of(17,0,0).toSecondOfDay()
      stopTime - startTime
    }
    val timezone = ZoneId.of("America/Chicago")
    val distributionProbability: IndexedSeq[Double] = {
      val inputs = Seq(25.0, 100.0, 75.0, 30.0, 7.0, 1.0, 0.5, 0.1)
      val total = inputs.sum
      inputs.map(_ / total).toIndexedSeq
    }
    val distributionCommits: IndexedSeq[Int] = Seq(0, 1, 2, 3, 4, 5, 6, 7).toIndexedSeq
    val seed = 5412376944304424827L
  }

  import Config._

  val random = new Random(seed)

  def main(args: Array[String]) = {
    // read holidays
    val holidays = Source.fromFile("holidays.txt").getLines
      .map(LocalDate.parse)
      .toSet

    // get all days in our operating range, minus holidays and weekends
    val days = IndexedSeq.iterate(startDate, ChronoUnit.DAYS.between(startDate, stopDate.plusDays(1L)).toInt)(_.plusDays(1L))
      .filter(!holidays.contains(_))
      .filter(_.getDayOfWeek() match {
        case DayOfWeek.SATURDAY => false
        case DayOfWeek.SUNDAY => false
        case _ => true
      })
      .toIndexedSeq

    println(distributionProbability)
    println(distributionCommits)
    println(timezone)

    val writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get("script.sh"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))))

    days.foreach(day => {
      val count = commitCount()
      println(s"$day: $count")
      for (i <- 0 until count) {
        val timestamp = commitForDay(day).atZone(timezone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        writer.print(getOutput(timestamp))
      }
    })

    writer.close()
  }

  def commitForDay(day: LocalDate): LocalDateTime = {
    val time = LocalTime.ofSecondOfDay(random.nextInt(stopAfter) + startTime)
    day.atTime(time)
  }

  def commitCount(): Int = {
    var distance = random.nextDouble()
    var index: Int = 0
    while (distributionProbability(index) < distance) {
      distance -= distributionProbability(index)
      index += 1
    }
    distributionCommits(index)
  }

  def getOutput(timestamp: String): String = {
    val q = "\""
    s"echo $q$timestamp$q >> data.txt\nexport GIT_COMMITTER_DATE=$q$timestamp$q\nexport GIT_AUTHOR_DATE=$q$timestamp$q\ngit add data.txt\ngit commit -m ${q}Committed on $timestamp$q\n"
  }
}
