package resources

import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import conversions.PartnerConversions
import org.coursera.example.Partner
import org.coursera.naptime.Errors
import org.coursera.naptime.Fields
import org.coursera.naptime.MultiGetReverseRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.protobuf.PartnerService
import org.coursera.protobuf.ids.PartnerId

import scala.concurrent.ExecutionContext

@Singleton
class PartnersResource @Inject() (
    partnerClient: PartnerService.PartnerServiceGrpc.PartnerServiceStub)(
    implicit ec: ExecutionContext, mat: Materializer)
  extends CourierCollectionResource[String, Partner] {

  override def resourceName = "partners"
  override def resourceVersion = 1
  override implicit lazy val Fields: Fields[Partner] = BaseFields
    .withReverseRelations(
      "instructors" -> MultiGetReverseRelation(
        resourceName = ResourceName("instructors", 1),
        ids = "$instructorIds"),
      "courses" -> MultiGetReverseRelation(
        resourceName = ResourceName("courses", 1),
        ids = "$courseIds"))

  def get(id: String) = Nap.get.async { context =>
    find(partnerIds = Some(Set(id)))
      .map(_.headOption.getOrElse(throw Errors.NotFound()))
      .map(Ok(_))
  }

  def multiGet(ids: Set[String]) = Nap.multiGet.async { context =>
    find(partnerIds = Some(ids)).map(Ok(_))
  }

  def getAll() = Nap.getAll.async { context =>
    find().map(Ok(_))
  }

  private[this] def find(
      partnerIds: Option[Set[String]] = None) = {
    val request = PartnerService.FindPartnersRequest(
      partnerIds = partnerIds.map(_.map(PartnerId(_)).toList).getOrElse(List.empty))
    partnerClient
      .findPartners(request)
      .map(_.partners.map(PartnerConversions.protoToCourier).map(Keyed.tupled))
  }

}
