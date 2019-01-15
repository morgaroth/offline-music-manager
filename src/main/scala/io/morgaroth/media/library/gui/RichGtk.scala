package io.morgaroth.media.library.gui

import cats.Monoid
import io.morgaroth.media.library.storage.Track
import org.gnome.gdk.Event
import org.gnome.gtk.{Unit => _, _}

trait RichGtk {

  implicit def strM = new Monoid[String] {
    override def empty = ""
    override def combine(x: String, y: String) = x + y
  }


  implicit class ds(in: Option[Track]) {
    def orEmpty(extractor: Track => String): String = {
      in.map(extractor).getOrElse("")
    }
  }

  implicit class RichGtkBtn[T <: Button](underlying: T) {
    def onClick(b: T => Unit): T = {
      underlying.connect(new Button.Clicked {
        override def onClicked(button: Button) = b(button.asInstanceOf[T])
      })
      underlying
    }

    def disabled: T = enabled(false)

    def enable: T = enabled(true)

    def enabled(condition: Boolean) = {
      underlying.setSensitive(condition)
      underlying
    }
  }

  implicit class RichWindow(window: Window) {
    def closeOnDeleteEvent(): Unit = {
      window.connect(new Window.DeleteEvent() {
        def onDeleteEvent(source: Widget, event: Event) = {
          Gtk.mainQuit()
          false
        }
      })
    }
  }

  implicit class RichCheckButton(btn: CheckButton) extends RichGtkBtn(btn) {
    def onToggle(a: (Boolean, CheckButton) => Unit): CheckButton = {
      btn.connect(new ToggleButton.Toggled {
        override def onToggled(toggleButton: ToggleButton) = {
          a(toggleButton.getActive, toggleButton.asInstanceOf[CheckButton])
        }
      })
      btn
    }

    def selected = {
      btn.setActive(true)
      btn
    }

    def unselected = {
      btn.setActive(false)
      btn
    }

    def select(condition: Boolean) = {
      btn.setActive(condition)
      btn
    }
  }

  implicit class RichEdit(underlying: org.gnome.gtk.Entry) {
    def enable: Entry = enabled(true)

    def disable = enabled(false)

    def enabled(condition: Boolean): Entry = {
      underlying.setEditable(condition)
      underlying.setSensitive(condition)
      underlying
    }

    def sensitive(condition: Boolean) = {
      underlying.setSensitive(condition)
      underlying
    }
  }

  def Btn(title: String, onClick: Button => Unit = _ => ()): Button = {
    new Button(title).onClick(onClick)
  }

  def Checkbox(title: String): CheckButton = {
    new org.gnome.gtk.CheckButton(title)
  }

  def Checkbox(title: String, onChange: (Boolean, CheckButton) => Unit): CheckButton = {
    Checkbox(title).onToggle(onChange)
  }

  def HorizontalLayout(cells: Int, elements: Widget*) = {
    val r = new HBox(false, cells)
    elements.foreach(r.add)
    r
  }

  def VerticalLayout(cells: Int, elements: Widget*) = {
    val r = new VBox(false, cells)
    elements.foreach(r.add)
    r
  }

  def Entry() = new Entry()

  def Entry(text: String) = new Entry(text)

  def L(text: String) = new Label(text)
}