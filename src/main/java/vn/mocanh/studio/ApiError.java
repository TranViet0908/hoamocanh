package vn.mocanh.studio;

public class ApiError extends RuntimeException {
  final int status;
  final Object detail;

  public ApiError(int status, String detail) {
    super(detail);
    this.status = status;
    this.detail = detail;
  }
}
