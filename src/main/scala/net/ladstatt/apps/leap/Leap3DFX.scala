package net.ladstatt.apps.leap

import com.leapmotion.leap.Controller
import java.net.URL
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.geometry.Point3D
import javafx.scene._
import javafx.scene.input.MouseEvent
import javafx.scene.layout._
import javafx.scene.paint._
import javafx.scene.transform.Rotate
import javafx.scene.transform.Translate
import javafx.stage.{Modality, StageStyle, Stage}
import javafx.util.Callback
import com.interactivemesh.jfx.importer.tds.TdsModelImporter
import javafx.event.{EventHandler, Event}
import scala.collection.JavaConversions._
import scala.util.{Random, Try}
import javafx.scene.control.{ListCell, ListView, ToolBar, Label}
import javafx.scene.shape.MeshView
import org.controlsfx.control.PropertySheet
import org.controlsfx.control.PropertySheet.Item
import scala.util.Failure
import scala.util.Success

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
 *         scala, maven port and some additional features by @rladstaetter
 */
object Leap3DFX {

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
    Application.launch(classOf[Leap3DFX], args: _*)
  }

}


trait MatrixOperation {

  def matrixRotateNode(n: Node, alf: Double, bet: Double, gam: Double) {
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
}


trait JfxUtils {

  def mkMaterial(diffuseColor: Color = Color.BISQUE, specularColor: Color = Color.LIGHTBLUE) = {
    val material = new PhongMaterial()
    material.setDiffuseColor(diffuseColor)
    material.setSpecularColor(specularColor)
    material
  }

  def mkRandColor = Color.rgb(Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

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
      Platform.exit
      event.consume
    }))
    stage
  }

  def mkCellFactoryCallback[T](listCellGenerator: ListView[T] => ListCell[T]) = new Callback[ListView[T], ListCell[T]]() {
    override def call(list: ListView[T]): ListCell[T] = listCellGenerator(list)
  }


}

object Colors extends JfxUtils {

  val RedMaterial = mkMaterial(Color.RED, Color.DARKRED)
  val GreenMaterial = {
    val m = mkMaterial(Color.GREEN, Color.GREENYELLOW)
    m
  }
}

case class Rail(val index: Int, id: String)
  extends SimpleLeapListener with MatrixOperation with ThreeDsTools with JfxUtils {

  val model3D = load3ds(getClass.getResource("rail.3ds")).get

  val pointLight = {
    val p = new PointLight(mkRandColor)
    p.setTranslateX(800)
    p.setTranslateY(-800)
    p.setTranslateZ(-1000)
    p
  }

  val points = mkMeasurementPoints(Random.nextInt(150))

  model3D.getChildren.addAll(points)

  lazy val ambientLight = {
    val p = new AmbientLight(mkRandColor)
    p.setTranslateX(-800)
    p.setTranslateY(800)
    p.setTranslateZ(1000)
    p
  }


  def mkMeasurementPoints(pointCount: Int): List[MeshView] = {

    def mkPoint(model: String, material: Material, xTransform: Int, yTransform: Int, zTransform: Int): MeshView = {
      val nodes = import3ds(getClass.getResource(model))
      val meshView = nodes(0).asInstanceOf[Group].getChildren.head.asInstanceOf[MeshView]
      meshView.setTranslateX(xTransform)
      meshView.setTranslateY(yTransform)
      meshView.setTranslateZ(zTransform)
      meshView.setMaterial(material)
      meshView
    }

    (for (i <- 0 until pointCount) yield {
      val point =
        if (Random.nextBoolean())
          mkPoint("detail.3ds", Colors.GreenMaterial, -Random.nextInt(1000), -Random.nextInt(300) + 200, -Random.nextInt(300) + 200)
        else
          mkPoint("error.3ds", Colors.RedMaterial, -Random.nextInt(1000), -Random.nextInt(300) + 200, -Random.nextInt(300) + 200)

      point.setOnMouseEntered(mkEventHandler[MouseEvent](
        e => {
          point.setScaleX(2)
          point.setScaleY(2)
          point.setScaleZ(2)
        }
      ))
      point.setOnMouseExited(mkEventHandler[MouseEvent](
        e => {
          point.setScaleX(1)
          point.setScaleY(1)
          point.setScaleZ(1)
        }
      ))
      point
    }).toList
  }

  def deLeap(controller: Controller) = {
    controller.removeListener(this)
  }

  def init3D(parent: Group, controller: Controller) = {
    parent.getChildren.clear()
    parent.getChildren.add(model3D)
    parent.getChildren.addAll(ambientLight, pointLight)
    controller.addListener(this)

    posHandLeftProperty.addListener(mkChangeListener[Point3D](
      (ov, t, t1) => execOnUIThread({
        val z = z1Property.getValue()
        model3D.setScaleX(1d + z / 200d)
        model3D.setScaleY(1d + z / 200d)
        model3D.setScaleZ(1d + z / 200d)
        val roll = rollLeftProperty.get()
        val pitch = -pitchLeftProperty.get()
        val yaw = -yawLeftProperty.get()
        matrixRotateNode(model3D, roll, pitch, yaw)
      }
      )))
  }


  lazy val mkOverview: FlowPane = {
    val fp = new FlowPane()
    fp.setHgap(5)
    fp.getChildren.addAll(new Label(id), mkToolBar)
    fp
  }

  def mkToolBar() = {
    val t = new ToolBar()
    t.getItems.addAll(
      for (i <- 0 until points.size) yield {
        val b = new Region()
        b.setPrefWidth(Random.nextInt(5))
        b.setPrefHeight(50)
        if (points(i).getMaterial == Colors.RedMaterial) {
          b.setStyle("-fx-background-color: red;")
        } else {
          b.setStyle("-fx-background-color: green;")
        }
        b.setOnMouseEntered(mkEventHandler[MouseEvent](
          e => {
            points(i).setScaleX(4)
            points(i).setScaleY(4)
            points(i).setScaleZ(4)
          }
        ))
        b.setOnMouseExited(mkEventHandler[MouseEvent](
          e => {
            points(i).setScaleX(1)
            points(i).setScaleY(1)
            points(i).setScaleZ(1)
          }
        ))
        b
      })
    t
  }
}


class RailListViewCell(sceneRoot: Group, leapController: Controller) extends ListCell[Rail] with JfxUtils {

  setOnMouseEntered(mkEventHandler(event => {
    if (getItem != null) {
      getItem().init3D(sceneRoot, leapController)
    }
  }))

  setOnMouseExited(mkEventHandler(event => {
    if (getItem != null) {
      getItem().deLeap(leapController)
    }
  }))

  override def updateItem(rail: Rail, empty: Boolean): Unit = {
    super.updateItem(rail, empty)
    if (rail != null) {
      setGraphic(rail.mkOverview)
    }
  }

}

trait ThreeDsTools {
  def import3ds(url: URL): Array[Node] = {
    val importer = new TdsModelImporter
    importer.read(url)
    val model = importer.getImport
    importer.close
    model
  }

  def load3ds(url: URL): Try[Group] = Try {
    new Group(import3ds(url): _*)
  }
}

class Leap3DFX extends Application with JfxUtils {
  val root = new Group
  private final val cameraXRotate: Rotate = new Rotate(0, 0, 0, 0, Rotate.X_AXIS)
  private final val cameraYRotate: Rotate = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS)
  private final val cameraPosition: Translate = new Translate(-1100, -400, -1000)
  val threeDScene = new Scene(root, 1300, 880, true)

  private final val leapController: Controller = new Controller
  private var dragStartX: Double = 0.0d
  private var dragStartY: Double = 0.0d
  private var dragStartRotateX: Double = 0.0d
  private var dragStartRotateY: Double = 0.0d

  val rails = mkRails(100)


  class MockItem[T](val name: String, value: T, itemType: Class[T]) extends Item {

    val oProp = new SimpleObjectProperty[java.lang.Object]

    setValue(value.asInstanceOf[java.lang.Object])

    override def getCategory = "category"

    override def getDescription = "description"

    override def getName = name

    override def getType: Class[_] = itemType

    override def getValue: java.lang.Object = oProp.get()

    override def setValue(o: java.lang.Object) = oProp.set(o)
  }


  def mkRail(idx: Int, id: String): Rail = {
    Rail(idx, id)
  }

  def mkRails(cnt: Int): Iterable[Rail] = {
    for (i <- 0 until cnt) yield mkRail(i, s"Rail $i")
  }

  def mkCockpit(sceneRoot: Group, leapController: Controller): BorderPane = {
    val bp = new BorderPane
    val lineBorderPropertySheet = new PropertySheet()

    val l = new ListView[Rail]
    l.getItems.addAll(rails)
    l.setCellFactory(mkCellFactoryCallback[Rail]((list: ListView[Rail]) => new RailListViewCell(sceneRoot, leapController)))
    bp.setTop(l)

    lineBorderPropertySheet.setModeSwitcherVisible(false)
    lineBorderPropertySheet.setSearchBoxVisible(false)
    lineBorderPropertySheet.setMaxHeight(java.lang.Double.MAX_VALUE)

    val titleProperty = new MockItem("Title", "a title", classOf[String])
    lineBorderPropertySheet.getItems().add(titleProperty)

    val colorProperty = new MockItem("Color", Color.RED, classOf[Color])
    lineBorderPropertySheet.getItems().add(colorProperty)

    val radiusProperty = new MockItem("Radius", 4.0, classOf[Double])
    lineBorderPropertySheet.getItems().add(radiusProperty)

    val thicknessProperty = new MockItem("Thickness", 5.0, classOf[Double])
    lineBorderPropertySheet.getItems().add(thicknessProperty)

    bp.setCenter(lineBorderPropertySheet)
    bp
  }

  def start(primaryStage: Stage) {
    primaryStage.setTitle("Leapmotion Motion app")

    val canvas = new Scene(mkCockpit(root, leapController), 500, 800)
    mkWindow(canvas).show


    val stops = List[Stop](new Stop(0, Color.DARKGRAY), new Stop(1, Color.BLACK))
    val lg1 = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops)

    threeDScene.setFill(lg1)
    val camera = new PerspectiveCamera
    camera.asInstanceOf[Node].getTransforms.addAll(cameraXRotate, cameraYRotate, cameraPosition)
    threeDScene.setCamera(camera)

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

    primaryStage.setScene(threeDScene)
    primaryStage.show
  }


  override def stop {
    rails.map(_.deLeap(leapController))
  }


}


