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
import javafx.scene.layout.AnchorPane
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
import javafx.scene.shape.{MeshView, TriangleMesh}

/**
 *
 * JavaFX 3D and Leap Motion, with JDK8
 *
 * @author José Pereda - @JPeredaDnr
 *         created on 12-feb-2014 17:10
 *
 *         See Leap Motion Controller and JavaFX: A new touch-less approach
 *         http://jperedadnr.blogspot.com.es/2013/06/leap-motion-controller-and-javafx-new.html
 *         and JavaFX 3D and Leap Motion: a short space adventure
 *         http://www.youtube.com/watch?v=TS5RvqDsEoU&feature=player_embedded
 */
object SLeap3D {

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
      }
    }
    case Success(null) => sys.error("field sys_paths was null.")
    case Failure(e) => throw e
  }

  /**
   * The main() method is ignored in correctly deployed JavaFX application.
   * main() serves only as fallback in case the application can not be
   * launched through deployment artifacts, e.g., in IDEs with limited FX
   * support. NetBeans ignores main().
   *
   * @param args the command line arguments
   */
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
  def mkWindow(scene: Scene, stageStyle: StageStyle = StageStyle.UNDECORATED, modality: Modality = Modality.NONE): Stage = {
    val stage = new Stage(stageStyle)
    //  stage.initModality(modality)
    stage.setScene(scene)
    //  stage.setOnCloseRequest(mkEventHandler(event => {
    // event.consume
    // }))

    stage
  }


}

class SLeap3D extends Application with JfxUtils {

  def mkMesh2(): Try[Group] = Try {
    new Group(new MeshView(createMesh(1000, 1000,100)))
  }

  def createMesh(w: Float, h: Float, d: Float): TriangleMesh = {
    if (w * h * d == 0) {
      null;
    } else {

      val hw = w / 2f;
      val hh = h / 2f;
      val hd = d / 2f;

      val x0 = 0f;
      val x1 = 1f / 4f;
      val x2 = 2f / 4f;
      val x3 = 3f / 4f;
      val x4 = 1f;
      val y0 = 0f;
      val y1 = 1f / 3f;
      val y2 = 2f / 3f;
      val y3 = 1f;

      val mesh = new TriangleMesh();
      mesh.getPoints().addAll(
        hw, hh, hd, //point A
        hw, hh, -hd, //point B
        hw, -hh, hd, //point C
        hw, -hh, -hd, //point D
        -hw, hh, hd, //point E
        -hw, hh, -hd, //point F
        -hw, -hh, hd, //point G
        -hw, -hh, -hd //point H
      );
      mesh.getTexCoords().addAll(
        x1, y0,
        x2, y0,
        x0, y1,
        x1, y1,
        x2, y1,
        x3, y1,
        x4, y1,
        x0, y2,
        x1, y2,
        x2, y2,
        x3, y2,
        x4, y2,
        x1, y3,
        x2, y3
      );
      mesh.getFaces().addAll(
        0, 10, 2, 5, 1, 9, //triangle A-C-B
        2, 5, 3, 4, 1, 9, //triangle C-D-B
        4, 7, 5, 8, 6, 2, //triangle E-F-G
        6, 2, 5, 8, 7, 3, //triangle G-F-H
        0, 13, 1, 9, 4, 12, //triangle A-B-E
        4, 12, 1, 9, 5, 8, //triangle E-B-F
        2, 1, 6, 0, 3, 4, //triangle C-G-D
        3, 4, 6, 0, 7, 3, //triangle D-G-H
        0, 10, 4, 11, 2, 5, //triangle A-E-C
        2, 5, 4, 11, 6, 6, //triangle C-E-G
        1, 9, 3, 4, 5, 8, //triangle B-D-F
        5, 8, 3, 4, 7, 3 //triangle F-D-H
      );
      mesh.getFaceSmoothingGroups().addAll(
        0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5
      );
      mesh;
    }
  }

  def mkMesh(): Try[Group] = Try {
    val model = new TdsModelImporter
    val hubbleUrl: URL = this.getClass.getResource("/com/jpl/leap3d/hst.3ds")
    model.read(hubbleUrl)
    val g = new Group(model.getImport: _*)
    model.close
    g
  }

  def start(primaryStage: Stage) {
    primaryStage.setTitle("Hubble 3D model - JavaFX - Leap Motion")

    val statsScene = new Scene(new Label("jodel"), 0, 0, false)


    val scene: Scene = new Scene(root, 1200, 700, true)

    scene.setFill(new ImagePattern(new Image(getClass.getResource("/com/jpl/leap3d/earth_by_Moguviel.jpg").toExternalForm)))

    leapController.addListener(listener)


    mkMesh match {
      case Success(model3D) => {
        val pointLight = new PointLight(Color.ANTIQUEWHITE)
        pointLight.setTranslateX(800)
        pointLight.setTranslateY(-800)
        pointLight.setTranslateZ(-1000)
        root.getChildren.addAll(model3D, pointLight)
        listener.posHandLeftProperty.addListener(mkChangeListener[Point3D](
          (ov, t, t1) => {
            Platform.runLater(new Runnable() {
              override def run(): Unit = {
                val z = listener.z1Property.getValue()
                model3D.setScaleX(1d + z / 200d)
                model3D.setScaleY(1d + z / 200d)
                model3D.setScaleZ(1d + z / 200d)
                val roll = listener.rollLeftProperty.get()
                val pitch = -listener.pitchLeftProperty.get()
                val yaw = -listener.yawLeftProperty.get()
                matrixRotateNode(model3D, roll, pitch, yaw)
              }
            }
            )
          }))

        listener.circleProperty.addListener(mkChangeListener[CircleGesture](
          (ov, t, t1) => {
            if (t1.radius() > 20 && t1.state().equals(State.STATE_STOP)) {
              if (!bRotating.getValue()) {
                Platform.runLater(new Runnable {
                  override def run(): Unit = {
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
                  }
                })
              } else if (timeline != null) {
                timeline.stop()
                System.out.println("stop rotating")
                bRotating.set(false)
              }
            }
          }))

        scene.addEventHandler(MouseEvent.ANY, mkEventHandler[MouseEvent](
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
      case Failure(ex) => println(ex.getStackTraceString)
    }

    val camera = new PerspectiveCamera
    camera.asInstanceOf[Node].getTransforms.addAll(cameraXRotate, cameraYRotate, cameraPosition)
    scene.setCamera(camera)

    mkWindow(scene).show()

    primaryStage.setScene(statsScene)
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

  private final val root  = new Group
  private final val cameraXRotate: Rotate = new Rotate(0, 0, 0, 0, Rotate.X_AXIS)
  private final val cameraYRotate: Rotate = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS)
  private final val cameraPosition: Translate = new Translate(-300, -550, -700)
  private final val listener = new SimpleLeapListener
  private final val leapController: Controller = new Controller
  private final val bRotating: BooleanProperty = new SimpleBooleanProperty(false)
  private var timeline: Timeline = null
  private var dragStartX: Double = .0
  private var dragStartY: Double = .0
  private var dragStartRotateX: Double = .0
  private var dragStartRotateY: Double = .0
}


