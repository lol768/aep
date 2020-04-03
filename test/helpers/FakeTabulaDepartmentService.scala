package helpers

import domain.tabula.Department
import services.tabula.TabulaDepartmentService
import services.tabula.TabulaDepartmentService.DepartmentsReturn
import warwick.core.timing.TimingContext

import scala.concurrent.Future

class FakeTabulaDepartmentService extends TabulaDepartmentService {
  override def getDepartments()(implicit t: TimingContext): Future[DepartmentsReturn] =
    Future.successful {
      Right(Seq(
        Department("ph", "Philosophy", "Philosophy", None),
        Department("lf", "LifeSci", "Life Sciences", None),
      ))
    }
}
