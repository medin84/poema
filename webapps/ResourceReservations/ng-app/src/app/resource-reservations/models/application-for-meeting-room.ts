import { IEntity, Observer, mdFormat, DATE_TIME_FORMAT } from '@nb/core';
import { Approval } from './approval';
import { Block } from './block';

export class ApplicationForMeetingRoom extends Approval {
    title: string;
    body: string;
    regNumber: string;
    appliedRegDate: Date;
    appliedAuthor: IEntity;
    recipient: IEntity;
    useFrom: Date;
    useTo: Date;
    tags: IEntity[];
    observers: { employee: IEntity }[];
    room: any;

    static convertToDto(m: ApplicationForMeetingRoom): any {
        return {
            id: m.id || null,
            title: m.title,
            body: m.body,
            regNumber: m.regNumber,
            appliedRegDate: mdFormat(m.appliedRegDate, DATE_TIME_FORMAT),
            appliedAuthor: m.appliedAuthor ? { id: m.appliedAuthor.id } : null,
            recipient: m.recipient ? { id: m.recipient.id } : null,
            useFrom: mdFormat(m.useFrom, DATE_TIME_FORMAT),
            useTo: mdFormat(m.useTo, DATE_TIME_FORMAT),
            tags: m.tags ? m.tags.map(it => { return { id: it.id }; }) : [],
            observers: Observer.convertToDtoList(m.observers),
            room: m.room ? { id: m.room.id } : null,
            ...Approval.convertToDto(m)
        };
    }
}
