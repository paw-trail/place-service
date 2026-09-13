package com.pawtrail.place.application.dto.input;

import com.pawtrail.place.domain.enums.PlaceStatus;

/**
 * 관리자가 고친 장소 값입니다.
 *
 * PATCH 는 보낸 것만 바꾸는 것이 계약이라 필드마다 세 상태가 있습니다.
 * 키를 아예 안 보낸 것과 명시적으로 null 을 보낸 것을 갈라야 합니다.
 * 갈리지 않으면 전화번호만 고치려고 보낸 요청이 소개문까지 지웁니다.
 *
 * 그래서 지울 수 있는 값은 값과 플래그를 짝으로 받습니다.
 * 플래그가 거짓이면 그 칸은 건드리지 않습니다.
 *
 * name 과 status 는 플래그가 없습니다.
 * 둘 다 지울 수 없는 값이라 요청 단계에서 명시적 null 이 막히고,
 * 그러면 여기 도착하는 null 은 안 보냈다는 뜻 하나만 남습니다.
 * user 의 프로필 수정이 닉네임을 같은 방식으로 다룹니다.
 *
 * 주소는 도로명과 지번이 한 덩어리입니다.
 * 정규화 값과 시도 코드가 둘에서 함께 나오므로 따로 받으면
 * 한쪽만 바뀐 주소에서 정규화 값을 만들게 됩니다.
 *
 * @param name            장소 이름입니다. null 이면 안 바꿉니다.
 * @param addressProvided 주소를 보냈는지입니다.
 * @param addressRoad     도로명 주소입니다.
 * @param addressJibun    지번 주소입니다. 도로명과 둘 다 비면 거부합니다.
 * @param telProvided     전화번호를 보냈는지입니다.
 * @param tel             전화번호입니다. 비우면 출처도 함께 지웁니다.
 * @param status          영업 상태입니다. null 이면 안 바꿉니다.
 */
public record PlaceAdminUpdateInput(String name,

                                    boolean addressProvided,
                                    String addressRoad,
                                    String addressJibun,

                                    boolean telProvided,
                                    String tel,

                                    boolean homepageProvided,
                                    String homepage,

                                    boolean reservationUrlProvided,
                                    String reservationUrl,

                                    boolean imageUrlProvided,
                                    String imageUrl,

                                    boolean overviewProvided,
                                    String overview,

                                    boolean businessHoursProvided,
                                    String businessHours,

                                    boolean closedDaysProvided,
                                    String closedDays,

                                    PlaceStatus status) {
}
