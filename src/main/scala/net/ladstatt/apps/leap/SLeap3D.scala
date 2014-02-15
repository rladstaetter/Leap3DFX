package net.ladstatt.apps.leap

import com.interactivemesh.jfx.importer.ImportException
import com.interactivemesh.jfx.importer.tds.TdsModelImporter
import com.leapmotion.leap.{Leap, CircleGesture, Controller}
import java.lang.reflect.Field
import java.net.URL
import javafx.animation.{Animation, KeyFrame, KeyValue, Timeline}
import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.{WritableValue, ChangeListener, ObservableValue}
import javafx.geometry.Point3D
import javafx.scene._
import javafx.scene.image.Image
import javafx.scene.input.MouseEvent
import javafx.scene.layout.{BorderPane, AnchorPane}
import javafx.scene.paint.Color
import javafx.scene.paint.ImagePattern
import javafx.scene.transform.Rotate
import javafx.scene.transform.Translate
import javafx.stage.{Modality, StageStyle, Stage}
import javafx.util.Duration
import com.interactivemesh.jfx.importer.tds.TdsModelImporter
import javafx.event.{EventHandler, Event}
import com.leapmotion.leap.Gesture.State
import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import javafx.scene.control.Label

/**
 *
 * Scala, JavaFX 3D and Leap Motion, with JDK8
 *
 * @author José Pereda - @JPeredaDnr
 *         created on 12-feb-2014 17:10
 *
 *         See Leap Motion Controller and JavaFX: A new touch-less approach
 *         http://jperedadnr.blogspot.com.es/2013/06/leap-motion-controller-and-javafx-new.html
 *         and JavaFX 3D and Leap Motion: a short space adventure
 *         http://www.youtube.com/watch?v=TS5RvqDsEoU&feature=player_embedded
 *
 *         scala port and some additional features by @rladstaetter
 */
object SLeap3D {

  // TODO: set your path here
  System.setProperty("java.library.path", "/Users/lad/LeapSDK+Examples_hotfix_public_mac_x64_0.8.1+6221/LeapSDK/lib");

  /* Work Around
  * http://blog.cedarsoft.com/2010/11/setting-java-library-path-programmatically/
  */

  Try {
    classOf[ClassLoader].getDeclaredField("sys_paths");
  } match {
    case Success(f) if (f != null) => {
      f.setAccessible(true)
      Try {
        f.set(null, null);
      } match {
        case Success(x) =>
        case Failure(e) => throw e
      }
    }
    case Success(null) => sys.error("field sys_paths was null.")
    case Failure(e) => throw e
  }

  def main(args: Array[String]) {
    Application.launch(classOf[SLeap3D], args: _*)
  }

}


trait JfxUtils {
  def mkChangeListener[T](onChangeAction: (ObservableValue[_ <: T], T, T) => Unit): ChangeListener[T] = {
    new ChangeListener[T]() {
      override def changed(observable: ObservableValue[_ <: T], oldValue: T, newValue: T) = {
        onChangeAction(observable, oldValue, newValue)
      }
    }
  }

  def mkEventHandler[E <: Event](f: E => Unit) = new EventHandler[E] {
    def handle(e: E) = f(e)
  }

  def execOnUIThread(f: => Unit) {
    Platform.runLater(new Runnable {
      override def run() = f
    })
  }

  /**
   * Creates a modal input window
   */
  def mkWindow(scene: Scene, stageStyle: StageStyle = StageStyle.DECORATED, modality: Modality = Modality.APPLICATION_MODAL): Stage = {
    val stage = new Stage(stageStyle)
    stage.initModality(modality)
    stage.setScene(scene)
    stage.setOnCloseRequest(mkEventHandler(event => {
      // event.consume
    }))
    stage
  }


}

class SLeap3D extends Application with JfxUtils {
  val root = new AnchorPane
  private final val cameraXRotate: Rotate = new Rotate(0, 0, 0, 0, Rotate.X_AXIS)
  private final val cameraYRotate: Rotate = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS)
  private final val cameraPosition: Translate = new Translate(-300, -550, -700)
  val header = new Label("jo")
  private final val listener = new SimpleLeapListener(header)
  private final val leapController: Controller = new Controller
  private final val bRotating: BooleanProperty = new SimpleBooleanProperty(false)
  private var timeline: Timeline = null
  private var dragStartX: Double = 0.0d
  private var dragStartY: Double = 0.0d
  private var dragStartRotateX: Double = 0.0d
  private var dragStartRotateY: Double = 0.0d

  def start(primaryStage: Stage) {
    primaryStage.setTitle("Leapmotion Motion app")


    //val bp = new BorderPane
   // bp.setTop(header)
  //  val canvas = new Scene(bp, 150, 100)
    val threeDScene = new Scene(root, 1300, 875)
  //  val stage = mkWindow(canvas)
   // stage.show()
    //http://images.fotocommunity.de/bilder/verkehr-fahrzeuge/eisenbahn/im-stahlwerk-vii-schlackekippen-iv-8131bcb9-1a27-44c1-bc40-5b56e217a201.jpg
    threeDScene.setFill(new ImagePattern(new Image(getClass.getResource("im-stahlwerk-vii-schlackekippen-iv-8131bcb9-1a27-44c1-bc40-5b56e217a201.jpg").toExternalForm)))
    val camera = new PerspectiveCamera
    camera.asInstanceOf[Node].getTransforms.addAll(cameraXRotate, cameraYRotate, cameraPosition)
    threeDScene.setCamera(camera)
    leapController.addListener(listener)
    Try {
      val importer = new TdsModelImporter
      importer.read(getClass.getResource("hst.3ds"))
      val model = importer.getImport
      importer.close
      new Group(model: _*)
    } match {
      case Failure(ex) => println(ex.getStackTraceString)
      case Success(model3D) => {
        val pointLight = new PointLight(Color.ANTIQUEWHITE)
        pointLight.setTranslateX(800)
        pointLight.setTranslateY(-800)
        pointLight.setTranslateZ(-1000)
        root.getChildren.addAll(model3D, pointLight)
        listener.posHandLeftProperty.addListener(mkChangeListener[Point3D](
          (ov, t, t1) => execOnUIThread({
            val z = listener.z1Property.getValue()
            model3D.setScaleX(1d + z / 200d)
            model3D.setScaleY(1d + z / 200d)
            model3D.setScaleZ(1d + z / 200d)
            val roll = listener.rollLeftProperty.get()
            val pitch = -listener.pitchLeftProperty.get()
            val yaw = -listener.yawLeftProperty.get()
            matrixRotateNode(model3D, roll, pitch, yaw)
          }
          )))

        listener.circleProperty.addListener(mkChangeListener[CircleGesture](
          (ov, t, t1) => {
            if (t1.radius() > 20 && t1.state().equals(State.STATE_STOP)) {
              if (!bRotating.getValue()) {
                execOnUIThread({
                  val d =
                    if (t1.pointable().direction().angleTo(t1.normal()) <= Math.PI / 4) {
                      -360d // clockwise
                    } else 360d
                  timeline = new Timeline(
                    List(
                      new KeyFrame(Duration.ZERO, new KeyValue(model3D.rotateProperty.asInstanceOf[WritableValue[Any]], model3D.getRotate())),
                      new KeyFrame(Duration.seconds(6 - t1.radius() / 20), new KeyValue(model3D.rotateProperty.asInstanceOf[WritableValue[Any]], model3D.getRotate() + d))
                    ): _*)
                  timeline.setCycleCount(Animation.INDEFINITE)
                  timeline.play()
                  System.out.println("rotating " + d + " r: " + t1.radius())
                  bRotating.set(true)
                })
              } else if (timeline != null) {
                timeline.stop()
                println("stop rotating")
                bRotating.set(false)
              }
            }
          }))

        // support mouse
        threeDScene.addEventHandler(MouseEvent.ANY, mkEventHandler[MouseEvent](
          (event) => {
            if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
              dragStartX = event.getSceneX()
              dragStartY = event.getSceneY()
              dragStartRotateX = cameraXRotate.getAngle()
              dragStartRotateY = cameraYRotate.getAngle()
            } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
              val xDelta = event.getSceneX() - dragStartX
              val yDelta = event.getSceneY() - dragStartY
              cameraXRotate.setAngle(dragStartRotateX - (yDelta * 0.7))
              cameraYRotate.setAngle(dragStartRotateY + (xDelta * 0.7))
            }
          }))
      }
    }

    primaryStage.setScene(threeDScene)
    primaryStage.show
  }

  private def matrixRotateNode(n: Node, alf: Double, bet: Double, gam: Double) {
    val A11: Double = Math.cos(alf) * Math.cos(gam)
    val A12: Double = Math.cos(bet) * Math.sin(alf) + Math.cos(alf) * Math.sin(bet) * Math.sin(gam)
    val A13: Double = Math.sin(alf) * Math.sin(bet) - Math.cos(alf) * Math.cos(bet) * Math.sin(gam)
    val A21: Double = -Math.cos(gam) * Math.sin(alf)
    val A22: Double = Math.cos(alf) * Math.cos(bet) - Math.sin(alf) * Math.sin(bet) * Math.sin(gam)
    val A23: Double = Math.cos(alf) * Math.sin(bet) + Math.cos(bet) * Math.sin(alf) * Math.sin(gam)
    val A31: Double = Math.sin(gam)
    val A32: Double = -Math.cos(gam) * Math.sin(bet)
    val A33: Double = Math.cos(bet) * Math.cos(gam)
    val d: Double = Math.acos((A11 + A22 + A33 - 1d) / 2d)
    if (d != 0d) {
      val den: Double = 2d * Math.sin(d)
      val p: Point3D = new Point3D((A32 - A23) / den, (A13 - A31) / den, (A21 - A12) / den)
      n.setRotationAxis(p)
      n.setRotate(Math.toDegrees(d))
    }
  }

  override def stop {
    leapController.removeListener(listener)
  }


}


