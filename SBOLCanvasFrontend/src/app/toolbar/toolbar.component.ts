import { AfterViewInit, Component, ElementRef, OnInit, QueryList, ViewChild } from '@angular/core';
import { GraphService } from '../graph.service';
import { FilesService } from '../files.service';
import { MatDialog } from '@angular/material';
import { SaveGraphComponent } from '../save-graph/save-graph.component';
import { LoadGraphComponent } from '../load-graph/load-graph.component';
import { UploadGraphComponent } from '../upload-graph/upload-graph.component';
import { DownloadGraphComponent } from '../download-graph/download-graph.component';
import { ExportComponent } from '../export/export.component';

export interface SaveDialogData {
  filename: string;
}
export interface LoadDialogData {
  file: File;
}

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.css']
})
export class ToolbarComponent implements OnInit, AfterViewInit {

  @ViewChild('backbone') backbone: ElementRef;

  filename: string;
  popupOpen: boolean;
  users: {};

  constructor(public graphService: GraphService, private filesService: FilesService, public dialog: MatDialog) { }

  ngOnInit() {
  }

  ngAfterViewInit() {
  }

  save(filename: string) {
    this.filesService.saveLocal(filename, this.graphService.getGraphXML());
  }

  load(file: File) {
    this.filesService.loadLocal(file, this.graphService);
  }

  openUploadDialog(): void {
    this.dialog.open(UploadGraphComponent, {});
  }

  openDownloadDialog(): void{
    this.dialog.open(DownloadGraphComponent, {
      data: null
    });
  }

  openSaveDialog(): void {
    const dialogRef = this.dialog.open(SaveGraphComponent, {
      data: { filename: this.filename }
    });

    this.popupOpen = true;
    dialogRef.afterClosed().subscribe(result => {
      this.popupOpen = false;
      if (result != null) {
        this.save(result);
      }
    });
  }

  openLoadDialog(): void {
    const dialogRef = this.dialog.open(LoadGraphComponent, {
      data: { file: null }
    });
    this.popupOpen = true;
    dialogRef.afterClosed().subscribe(result => {
      this.popupOpen = false;
      if (result != null) {
        this.load(result);
      }
    });
  }

  openExportDialog(): void {
    this.dialog.open(ExportComponent, {});
  }

  zoomChanged($event) {
    let number = parseInt($event.target.value);
    if (!isNaN(number)) {
      const percent = number / 100;
      this.graphService.setZoom(percent);
    }

    // if they entered nonsense the zoom doesn't change, which
    // means angular won't refresh the input box on its own
    $event.target.value = this.getZoomDisplayValue();
  }

  getZoomDisplayValue() {
    let percent = this.graphService.getZoom() * 100;
    let string = percent.toFixed(0);
    return string.toString() + '%';
  }
}
