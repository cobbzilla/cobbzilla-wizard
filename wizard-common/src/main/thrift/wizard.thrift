namespace java org.cobbzilla.wizard.thrift
namespace rb CobbzillaWizard
namespace php CobbzillaWizard

enum tSortOrder {
  ASC = 0,
  DESC = 1,
}

struct tResultPage {
  1: i32 pageNumber,
  2: i32 pageSize,
  3: string sortField,
  4: tSortOrder sortOrder,
  5: string filter
}
