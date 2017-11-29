package com.tinylabproductions.uploader.ansi

object Ansi {

  /**
    * Common methods for all types of Ansi modifications.
    */
  sealed trait AnsiCode {
    def beginInt: Int
    def endInt: Int
    def start: String = s"\u001b[${beginInt}m"
    def end: String = s"\u001b[${endInt}m"

    def apply(s: String) = s"$start$s$end"
  }

  /**
    * Modifiers of output that are not related to color.
    * Only kept the ones that work on most environments.
    */
  sealed abstract class Modifier(val beginInt: Int, val endInt: Int) extends Ansi.AnsiCode
  object Modifier {
    case object BOLD extends Modifier(1, 22) // 21 isn't widely supported and 22 does the same thing
    case object UNDERLINE extends Modifier(4, 24)
    case object INVERSE extends Modifier(7, 27)
  }

  /**
    * Foreground colors.
    */
  sealed abstract class Color(val beginInt: Int) extends Ansi.AnsiCode {
    override def endInt: Int = 39
  }
  object Color {
    case object BLACK extends Color(30)
    case object RED extends Color(31)
    case object GREEN extends Color(32)
    case object YELLOW extends Color(33)
    case object BLUE extends Color(34)
    case object MAGENTA extends Color(35)
    case object CYAN extends Color(36)
    case object WHITE extends Color(37)
    case object GRAY extends Color(90)
    case object GREY extends Color(90)
  }

  val cursorUp: String = cursorUp(1)

  def cursorUp(rows: Int): String = escape('A', rows)

  val cursorDown: String = cursorDown(1)

  def cursorDown(rows: Int): String = escape('B', rows)

  val cursorRight: String = cursorRight(1)

  def cursorRight(cols: Int): String = escape('C', cols)

  val cursorLeft: String = cursorLeft(1)

  def cursorLeft(cols: Int): String = escape('D', cols)

  def setCursorPosition(x: Int, y: Int): String = escape('H', y, x)

  val eraseScreen: String = escape('J', 2)

  /**
    * Erase from cursor down
    */
  val eraseScreenDown: String = escape('J', 0)

  /**
    * Erase from cursor up
    */
  val eraseScreenUp: String = escape('J', 1)

  val eraseLine: String = escape('K', 2)

  /**
    * From line start to cursor
    */
  val eraseLineStart: String = escape('K', 1)

  /**
    * From cursor to line end
    */
  val eraseLineEnd: String = escape('K', 0)


  private def escape(command: Char, options: Int*) = {
    val sb = new StringBuilder(6)
    sb.append('\u001b')
    sb.append('[')
    optionsToValue(sb, options:_*)
    sb.append(command)
    sb.toString
  }

  private def optionsToValue(sb: StringBuilder, options: Int*) = {
    if (options.length == 1) sb.append(options(0))
    else {
      sb.append(options(0))
      var i = 1
      while (i < options.length) {
        sb.append(';')
        sb.append(options(i))

        i += 1
      }
    }
  }
}
