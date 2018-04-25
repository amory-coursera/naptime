package conversions

import org.coursera.naptime.RequestPagination
import org.coursera.naptime.ResponsePagination
import org.coursera.protobuf

object PagingConversions {

  def naptimeToProto(paging: RequestPagination): protobuf.pagination.RequestPagination =
    protobuf.pagination.RequestPagination(start = paging.start, limit = paging.limit)

  def naptimeToProto(paging: ResponsePagination): protobuf.pagination.ResponsePagination =
    protobuf.pagination.ResponsePagination(next = paging.next, total = paging.total)

  def protoToNaptime(paging: protobuf.pagination.ResponsePagination): ResponsePagination =
    ResponsePagination(next = paging.next, total = paging.total)
}
