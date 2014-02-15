package net.ladstatt.apps.leap

import com.leapmotion.leap.Controller
import java.util.LinkedList
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.geometry.Point3D
import com.leapmotion.leap.Listener
import com.leapmotion.leap.Gesture
import com.leapmotion.leap.Frame
import com.leapmotion.leap.Screen
import com.leapmotion.leap.Hand
import com.leapmotion.leap.Vector
import com.leapmotion.leap.Gesture.Type
import com.leapmotion.leap.CircleGesture
import scala.collection.JavaConversions._
import javafx.scene.control.Label

/**
 *
 * @author José Pereda Llamas
 *         Created on 10-abr-2013 - 19:58:55
 */
class SimpleLeapListener(header : Label) extends Listener with JfxUtils {

  def circleProperty: ObservableValue[CircleGesture] = {
    return circle
  }

  def posHandLeftProperty: ObservableValue[Point3D] = {
    return posHandLeft
  }

  def yawLeftProperty: DoubleProperty = {
    return yawLeft
  }

  def pitchLeftProperty: DoubleProperty = {
    return pitchLeft
  }

  def rollLeftProperty: DoubleProperty = {
    return rollLeft
  }

  def z1Property: DoubleProperty = {
    return z1
  }

  override def onConnect(controller: Controller) {
    controller.enableGesture(Gesture.Type.TYPE_CIRCLE)
    controller.enableGesture(Gesture.Type.TYPE_SWIPE)
  }

  override def onFrame(controller: Controller) {
    val frame: Frame = controller.frame
    if (!frame.hands.isEmpty) {
      numHands.set(frame.hands.count)
      val screen: Screen = controller.locatedScreens.get(0)
      if (screen != null && screen.isValid) {
        val hand =
          if (numHands.get > 1)
            frame.hands.leftmost
          else
            frame.hands.get(0)

        z1.set(hand.palmPosition.getZ)
        pitchLeftAverage.add(hand.direction.pitch)
        rollLeftAverage.add(hand.palmNormal.roll)
        yawLeftAverage.add(hand.direction.yaw)
        if (pitchLeftAverage.size > 0) pitchLeft.set(pitchLeftAverage.sum / pitchLeftAverage.size)
        if (rollLeftAverage.size > 0) rollLeft.set(rollLeftAverage.sum / rollLeftAverage.size)
        if (yawLeftAverage.size > 0) yawLeft.set(yawLeftAverage.sum / yawLeftAverage.size)
        val intersect: Vector = screen.intersect(hand.palmPosition, hand.direction, true)
        posLeftAverage.add(intersect)
        val avIntersect: Vector = vAverage(posLeftAverage)
        posHandLeft.setValue(new Point3D(screen.widthPixels * Math.min(1d, Math.max(0d, avIntersect.getX)), screen.heightPixels * Math.min(1d, Math.max(0d, (1d - avIntersect.getY))), hand.palmPosition.getZ))
      }
    }

    for (gesture <- frame.gestures
         if (numHands.get > 1 && (gesture.`type` == Type.TYPE_CIRCLE))) {
      val cGesture = new CircleGesture(gesture)
      for (h <- cGesture.hands if (h == frame.hands.rightmost)) {
        circle.set(cGesture)
      }
    }
    /*
    for (gesture <- frame.gestures) {
      gesture.`type` match {
        case Type.TYPE_INVALID => println(s"type: INVALID")
        case Type.TYPE_SWIPE => execOnUIThread {
          header.setText(s"type: SWIPE")
        }
        case Type.TYPE_CIRCLE => execOnUIThread {
          println("jodel")
          header.setText(s"type: CIRCLE")
        }
        case Type.TYPE_SCREEN_TAP => println(s"type: SCREEN_TAP")
        case Type.TYPE_KEY_TAP => println(s"type: KEY_TAP")
        case x => println("UNKNOWN TYPE")
      }
    }  */

  }

  private def vAverage(vectors: LimitQueue[Vector]): Vector = {
    val (vx, vy, vz) = vectors.foldLeft((0f, 0f, 0f))((acc, v) => (acc._1 + v.getX, acc._2 + v.getY, acc._3 + v.getZ))
    new Vector(vx / vectors.size, vy / vectors.size, vz / vectors.size)
  }

  private final val z1: DoubleProperty = new SimpleDoubleProperty(0d)
  private final val posHandLeft: ObjectProperty[Point3D] = new SimpleObjectProperty[Point3D]
  private final val pitchLeft: DoubleProperty = new SimpleDoubleProperty(0d)
  private final val rollLeft: DoubleProperty = new SimpleDoubleProperty(0d)
  private final val yawLeft: DoubleProperty = new SimpleDoubleProperty(0d)
  private final val posLeftAverage: LimitQueue[Vector] = new LimitQueue[Vector](30)
  private final val pitchLeftAverage: LimitQueue[Double] = new LimitQueue[Double](30)
  private final val rollLeftAverage: LimitQueue[Double] = new LimitQueue[Double](30)
  private final val yawLeftAverage: LimitQueue[Double] = new LimitQueue[Double](30)
  private final val circle: ObjectProperty[CircleGesture] = new SimpleObjectProperty[CircleGesture]
  private final val numHands: IntegerProperty = new SimpleIntegerProperty(0)

  private class LimitQueue[E](limit: Int = 0) extends LinkedList[E] {

    override def add(o: E): Boolean = {
      super.add(o)
      while (size > limit) {
        super.remove
      }
      true
    }


  }

}

